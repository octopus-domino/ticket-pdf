package ru.ticket.pdf.font;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

@Service
public class DejaVuFontProvider implements FontProvider {

    private static final String DEFAULT_FONT = "Arial";
    private static final String DEFAULT_FILE = "/fonts/DejaVuSans.ttf";

    private static final Map<String, String> FILES = new HashMap<>();

    static {
        FILES.put("Arial",           "/fonts/DejaVuSans.ttf");
        FILES.put("Helvetica",       "/fonts/DejaVuSans.ttf");
        FILES.put("Verdana",         "/fonts/DejaVuSans.ttf");
        FILES.put("Tahoma",          "/fonts/DejaVuSans.ttf");
        FILES.put("Trebuchet MS",    "/fonts/DejaVuSans.ttf");
        FILES.put("Times New Roman", "/fonts/DejaVuSerif.ttf");
        FILES.put("Georgia",         "/fonts/DejaVuSerif.ttf");
        FILES.put("Courier New",     "/fonts/DejaVuSansMono.ttf");
        FILES.put("Marck Script", "/fonts/MarckScript-Regular.ttf");
        FILES.put("Zhi Mang Xing", "/fonts/ZhiMangXing-Regular.ttf");
    }

    /** Кэш байтов TTF — без PDDocument, живёт долго, безопасен. */
    private final Map<String, byte[]> bytesCache = new HashMap<>();

    /**
     * Кэш шрифтов, привязанных к PDDocument.
     *
     * Ключ — сам документ. WeakHashMap позволяет GC-собрать запись, когда
     * документ больше не используется (после save + close). Значение (Map
     * с PDFont) НЕ держит ссылку на ключ, поэтому entry реально собирается.
     *
     * synchronized — потому что WeakHashMap не потокобезопасен, а операции
     * с одним PDDocument и так должны быть сериализованы (PDFBox не
     * thread-safe per document).
     */
    private final Map<PDDocument, Map<String, PDFont>> documentCache =
            Collections.synchronizedMap(new WeakHashMap<>());

    @Override
    public PDFont getFont(PDDocument doc, String family) throws IOException {
        if (doc == null) throw new IllegalArgumentException("doc must not be null");

        String key = normalize(family);

        synchronized (documentCache) {
            Map<String, PDFont> perDoc = documentCache.get(doc);
            if (perDoc == null) {
                perDoc = new HashMap<>(4);
                documentCache.put(doc, perDoc);
            }
            PDFont cached = perDoc.get(key);
            if (cached != null) return cached;

            byte[] ttf = loadTtf(key);
            if (ttf == null) {
                throw new IOException(
                        "Шрифт не найден. Положите DejaVuSans.ttf в src/main/resources/fonts/");
            }

            PDFont font = PDType0Font.load(doc, new ByteArrayInputStream(ttf), true);
            perDoc.put(key, font);
            return font;
        }
    }

    @Override
    public String resolveFamily(String requested) {
        if (requested == null || requested.isBlank()) return DEFAULT_FONT;
        return FILES.containsKey(requested) ? requested : DEFAULT_FONT;
    }

    private String normalize(String family) {
        if (family == null || family.isBlank()) return DEFAULT_FONT;
        return FILES.containsKey(family) ? family : DEFAULT_FONT;
    }

    private byte[] loadTtf(String family) {
        synchronized (bytesCache) {
            byte[] cached = bytesCache.get(family);
            if (cached != null) return cached;

            String path = FILES.getOrDefault(family, DEFAULT_FILE);
            try (InputStream in = getClass().getResourceAsStream(path)) {
                if (in == null) return null;
                byte[] bytes = in.readAllBytes();
                bytesCache.put(family, bytes);
                return bytes;
            } catch (IOException e) {
                return null;
            }
        }
    }
}
