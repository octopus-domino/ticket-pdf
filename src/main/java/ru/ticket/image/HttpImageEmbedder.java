package ru.ticket.image;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Встраивает внешние http(s)-изображения в SVG как data URL.
 *
 * <p>Безопасность (SSRF-защита): - только http/https; - hostname резолвится ДО запроса, все IP
 * проверяются; - лимит размера ответа (10 MB); - таймауты на connect и request; - парсер SVG с
 * отключёнными DTD/внешними сущностями (XXE).
 *
 * <p>Память: - кэш url → data URL ограничен по байтам (default 64 MB) и по количеству; - вытеснение
 * не LRU, а best-effort: при переполнении удаляются случайные записи до возврата под лимит
 * (ConcurrentHashMap без накладных расходов на порядок). Для одноразового экспорта этого
 * достаточно; для долгоживущего сервиса — заменить на Caffeine.
 */
@Service
public class HttpImageEmbedder implements ImageEmbedder {

  private static final String TRANSPARENT_PNG =
      "data:image/png;base64,"
          + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=";

  private static final String SVG_NS = "http://www.w3.org/2000/svg";
  private static final String XLINK_NS = "http://www.w3.org/1999/xlink";

  private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024; // 10 MB

  /** Символы → байты: data URL — строка UTF-16, каждый символ ~2 байта. */
  private static final long CACHE_BYTES_PER_CHAR = 2L;

  private final PublicImageDownloader downloader;

  private final long maxCacheBytes;

  private final Map<String, String> cache = new ConcurrentHashMap<>();
  private final AtomicLong cacheBytes = new AtomicLong(0);

  public HttpImageEmbedder(
      @Value("${app.image-embedder.max-cache-bytes:67108864}") long maxCacheBytes,
      PublicImageDownloader downloader) {
    this.downloader = downloader;
    this.maxCacheBytes = Math.max(1_048_576L, maxCacheBytes); // минимум 1 MB
  }

  @Override
  public String embed(String svg) {
    try {
      Document doc = parse(svg);
      boolean changed = embedAllImages(doc);
      return changed ? serialize(doc) : svg;
    } catch (IllegalArgumentException e) {
      throw e;
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
      if (href == null) throw new IllegalArgumentException("Image source is missing or unsupported; use an embedded PNG/JPEG or a public HTTP(S) image");
      boolean remote = href.startsWith("http://") || href.startsWith("https://");
      // Batik/FOP requires SVG 1.1 xlink:href even for already embedded SVG 2 images.
      // Do not download data URLs; preserve their bytes and normalize both attributes.
      if (!remote && !href.startsWith("data:image/")) continue;
      String dataUrl = remote ? getOrDownload(href) : href;
      if (dataUrl.equals(img.getAttributeNS(XLINK_NS, "href"))
          && dataUrl.equals(img.getAttribute("href"))) continue;
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
   * Кэш-hit возвращает сразу. При miss — скачиваем БЕЗ lock (network под нагрузкой), потом кладём в
   * кэш через bounded put.
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
      var image = downloader.download(url);
      return "data:"
          + image.mime()
          + ";base64,"
          + Base64.getEncoder().encodeToString(image.bytes());
    } catch (Exception error) {
      org.slf4j.LoggerFactory.getLogger(getClass())
          .warn("image.download_failed url={} reason={}", url, error.getMessage());
      return TRANSPARENT_PNG;
    }
  }
}
