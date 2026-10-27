package ru.ticket.image;

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
import java.util.HashMap;
import java.util.Map;

/**
 * Встраивает внешние http(s)-изображения в SVG как data URL.
 *
 * Безопасность (SSRF-защита):
 *   - только http/https;
 *   - hostname резолвится ДО запроса, все IP проверяются:
 *     loopback, link-local (в т.ч. 169.254.169.254), site-local,
 *     multicast, unique-local IPv6, CGNAT — блокируются;
 *   - лимит размера ответа (10 MB);
 *   - таймауты на connect и на request;
 *   - парсер SVG с отключёнными DTD/внешними сущностями (XXE).
 *
 * Остаточный риск: DNS rebinding (между проверкой и connect).
 * Для полного устранения нужен connection через заранее
 * зафиксированный IP с Host-заголовком — вне scope MVP.
 */
@Service
public class HttpImageEmbedder implements ImageEmbedder {

    private static final String TRANSPARENT_PNG =
            "data:image/png;base64," +
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=";

    private static final String SVG_NS = "http://www.w3.org/2000/svg";
    private static final String XLINK_NS = "http://www.w3.org/1999/xlink";

    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024; // 10 MB

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final Map<String, String> cache = new HashMap<>();

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
        // XXE protection
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

            String dataUrl = cache.computeIfAbsent(href, this::downloadAsDataUrl);
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

            // SSRF protection: резолвим и проверяем все IP до запроса.
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
            // 169.254.0.0/16 — link-local / cloud metadata
            if (b0 == 169 && b1 == 254) return false;
            // 100.64.0.0/10 — CGNAT
            if (b0 == 100 && b1 >= 64 && b1 <= 127) return false;
            // 0.0.0.0/8
            if (b0 == 0) return false;
        }

        if (b.length == 16) {
            int b0 = b[0] & 0xFF;
            // fc00::/7 — unique local
            if ((b0 & 0xFE) == 0xFC) return false;
        }

        return true;
    }
}