package ru.ticket.pdf.font;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

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
    }

    private final Map<String, byte[]> bytesCache = new HashMap<>();
    private final Map<PDDocument, Map<String, PDFont>> documentCache = new HashMap<>();

    @Override
    public PDFont getFont(PDDocument doc, String family) throws IOException {
        String key = normalize(family);

        Map<String, PDFont> cache = documentCache
                .computeIfAbsent(doc, d -> new HashMap<>());

        PDFont cached = cache.get(key);
        if (cached != null) return cached;

        byte[] ttf = loadTtf(key);
        if (ttf == null) throw new IOException(
                "Шрифт не найден. Положите DejaVuSans.ttf в src/main/resources/fonts/");

        PDFont font = PDType0Font.load(doc, new ByteArrayInputStream(ttf), true);
        cache.put(key, font);
        return font;
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
        return bytesCache.computeIfAbsent(family, k -> {
            String path = FILES.getOrDefault(k, DEFAULT_FILE);
            try (InputStream in = getClass().getResourceAsStream(path)) {
                return in != null ? in.readAllBytes() : null;
            } catch (IOException e) {
                return null;
            }
        });
    }
}