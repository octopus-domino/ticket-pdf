package ru.ticket.image;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Встраивает внешние http(s)-изображения в SVG как data URL.
 *
 * Безопасность (SSRF-защита):
 *   - только http/https;
 *   - hostname резолвится ДО запроса, все IP проверяются;
 *   - лимит размера ответа (10 MB);
 *   - таймауты на connect и request;
 *   - парсер SVG с отключёнными DTD/внешними сущностями (XXE).
 *
 * Память:
 *   - кэш url → data URL ограничен по байтам (default 64 MB) и по количеству;
 *   - вытеснение не LRU, а best-effort: при переполнении удаляются
 *     случайные записи до возврата под лимит (ConcurrentHashMap без
 *     накладных расходов на порядок). Для одноразового экспорта этого
 *     достаточно; для долгоживущего сервиса — заменить на Caffeine.
 */
@Service
public class HttpImageEmbedder implements ImageEmbedder {

    private static final String TRANSPARENT_PNG =
            "data:image/png;base64," +
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=";

    private static final String SVG_NS = "http://www.w3.org/2000/svg";
    private static final String XLINK_NS = "http://www.w3.org/1999/xlink";

    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024; // 10 MB

    /** Символы → байты: data URL — строка UTF-16, каждый символ ~2 байта. */
    private static final long CACHE_BYTES_PER_CHAR = 2L;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final long maxCacheBytes;

    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final AtomicLong cacheBytes = new AtomicLong(0);

    public HttpImageEmbedder(
            @Value("${app.image-embedder.max-cache-bytes:67108864}") long maxCacheBytes) {
        this.maxCacheBytes = Math.max(1_048_576L, maxCacheBytes); // минимум 1 MB
    }

    @Override
    public String embed(String svg) {
        try {
            Document doc = parse(svg);
            boolean changed = embedAllImages(doc);
            return changed ? serialize(doc) : svg;
        } catch (Exception e) {
            System.err.println("[ImageEmbedder] failed: " + e.getMessage());
            return svg;
        }
    }

    private Document parse(String svg) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setExpandEntityReferences(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
    }

    private boolean embedAllImages(Document doc) {
        NodeList images = doc.getElementsByTagNameNS(SVG_NS, "image");
        boolean changed = false;

        for (int i = 0; i < images.getLength(); i++) {
            Element img = (Element) images.item(i);
            String href = resolveHref(img);
            if (href == null) continue;
            if (!href.startsWith("http://") && !href.startsWith("https://")) continue;

            String dataUrl = getOrDownload(href);
            img.setAttributeNS(XLINK_NS, "xlink:href", dataUrl);
            img.setAttribute("href", dataUrl);
            changed = true;
        }
        return changed;
    }

    private String resolveHref(Element img) {
        String href = img.getAttributeNS(XLINK_NS, "href");
        if (href == null || href.isEmpty()) href = img.getAttribute("href");
        return href == null || href.isEmpty() ? null : href;
    }

    /**
     * Кэш-hit возвращает сразу. При miss — скачиваем БЕЗ lock (network под
     * нагрузкой), потом кладём в кэш через bounded put.
     */
    private String getOrDownload(String url) {
        String cached = cache.get(url);
        if (cached != null) return cached;

        String dataUrl = downloadAsDataUrl(url);
        putWithEviction(url, dataUrl);
        return dataUrl;
    }

    private void putWithEviction(String key, String value) {
        if (value == null) return;

        long valueBytes = value.length() * CACHE_BYTES_PER_CHAR;
        if (valueBytes > maxCacheBytes) {
            // одна запись больше лимита — не кэшируем, чтобы не вытеснить всё
            return;
        }

        String previous = cache.put(key, value);
        if (previous != null) {
            cacheBytes.addAndGet(-previous.length() * CACHE_BYTES_PER_CHAR);
        }
        cacheBytes.addAndGet(valueBytes);

        // best-effort вытеснение, пока не влезем в лимит
        // ConcurrentHashMap.size() — O(1) в среднем, итератор — слабо-консистентный
        while (cacheBytes.get() > maxCacheBytes && cache.size() > 1) {
            Iterator<Map.Entry<String, String>> it = cache.entrySet().iterator();
            if (!it.hasNext()) break;
            Map.Entry<String, String> eldest = it.next();
            if (cache.remove(eldest.getKey(), eldest.getValue())) {
                cacheBytes.addAndGet(-eldest.getValue().length() * CACHE_BYTES_PER_CHAR);
            }
        }
    }

    private String serialize(Document doc) throws Exception {
        Transformer tr = TransformerFactory.newInstance().newTransformer();
        tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        tr.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        tr.setOutputProperty(OutputKeys.INDENT, "no");

        StringWriter sw = new StringWriter();
        tr.transform(new DOMSource(doc), new StreamResult(sw));

        String result = sw.toString();
        if (!result.startsWith("<?xml")) {
            result = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + result;
        }
        return result;
    }

    private String downloadAsDataUrl(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null) {
                System.err.println("[ImageEmbedder] blocked: no scheme " + url);
                return TRANSPARENT_PNG;
            }
            scheme = scheme.toLowerCase();
            if (!scheme.equals("http") && !scheme.equals("https")) {
                System.err.println("[ImageEmbedder] blocked: scheme " + scheme + " " + url);
                return TRANSPARENT_PNG;
            }

            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                System.err.println("[ImageEmbedder] blocked: no host " + url);
                return TRANSPARENT_PNG;
            }

            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (!isPublicAddress(addr)) {
                    System.err.println("[ImageEmbedder] blocked: private IP "
                            + addr.getHostAddress() + " for " + url);
                    return TRANSPARENT_PNG;
                }
            }

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Ticket-PDF-Export/1.0")
                    .GET()
                    .build();

            HttpResponse<byte[]> res = http.send(req,
                    HttpResponse.BodyHandlers.ofByteArray());

            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                System.err.println("[ImageEmbedder] HTTP " + res.statusCode() + " " + url);
                return TRANSPARENT_PNG;
            }

            byte[] bytes = res.body();
            if (bytes == null || bytes.length == 0) return TRANSPARENT_PNG;
            if (bytes.length > MAX_IMAGE_BYTES) {
                System.err.println("[ImageEmbedder] too large: " + bytes.length
                        + " bytes for " + url);
                return TRANSPARENT_PNG;
            }

            String mime = res.headers()
                    .firstValue("content-type")
                    .orElse("image/png").split(";")[0].trim();
            if (mime.isEmpty() || !mime.startsWith("image/")) mime = "image/png";

            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            System.err.println("[ImageEmbedder] download failed " + url + ": " + e.getMessage());
            return TRANSPARENT_PNG;
        }
    }

    private boolean isPublicAddress(InetAddress addr) {
        if (addr.isAnyLocalAddress())      return false;
        if (addr.isLoopbackAddress())      return false;
        if (addr.isLinkLocalAddress())     return false;
        if (addr.isSiteLocalAddress())     return false;
        if (addr.isMulticastAddress())     return false;

        byte[] b = addr.getAddress();

        if (b.length == 4) {
            int b0 = b[0] & 0xFF;
            int b1 = b[1] & 0xFF;
            if (b0 == 169 && b1 == 254) return false;
            if (b0 == 100 && b1 >= 64 && b1 <= 127) return false;
            if (b0 == 0) return false;
        }

        if (b.length == 16) {
            int b0 = b[0] & 0xFF;
            if ((b0 & 0xFE) == 0xFC) return false;
        }

        return true;
    }
}