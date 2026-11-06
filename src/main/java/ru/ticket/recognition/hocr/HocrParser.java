package ru.ticket.recognition.hocr;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;
import ru.ticket.recognition.OcrEngine.StyleHints;
import ru.ticket.recognition.OcrEngine.Word;

/**
 * Разбирает hOCR-выдачу Tesseract в {@link Word}-список.
 *
 * <p>hOCR — XHTML-подобный формат: {@code <span class="ocrx_word"
 * title="bbox ...; x_wconf ...">} внутри {@code <span class="ocr_line">}.
 * Дополнительные атрибуты шрифта ({@code x_font, x_fsize, x_bold,
 * x_italic}) появляются только при {@code -c hocr_font_info=1} и ненадёжны.
 * Парсер терпим к их отсутствию — тогда отдаётся {@link StyleHints#NONE}.
 */
@Component
public class HocrParser {

    public List<Word> parse(String hocrXml) {
        if (hocrXml == null || hocrXml.isBlank()) return List.of();

        Document doc = Jsoup.parse(hocrXml, "", Parser.htmlParser());
        List<Word> words = new ArrayList<>();
        int lineIndex = 0;

        for (Element line : doc.select(".ocr_line, .ocrx_line")) {
            String lineId = "L" + lineIndex++;
            for (Element wordEl : line.select(".ocrx_word")) {
                parseWord(wordEl, lineId).ifPresent(words::add);
            }
        }
        return words;
    }

    private Optional<Word> parseWord(Element el, String lineId) {
        String title = el.attr("title");
        if (title.isBlank()) return Optional.empty();

        Map<String, String> props = parseTitle(title);
        int[] bbox = parseBbox(props.get("bbox"));
        if (bbox == null) return Optional.empty();

        String text = el.text().strip();
        if (text.isEmpty()) return Optional.empty();

        double confidence = parseDouble(props.getOrDefault("x_wconf", "0"), 0);
        return Optional.of(new Word(
                text,
                bbox[0], bbox[1],
                bbox[2] - bbox[0],
                bbox[3] - bbox[1],
                confidence,
                lineId,
                parseHints(props)));
    }

    private static Map<String, String> parseTitle(String title) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String chunk : title.split(";")) {
            String part = chunk.trim();
            if (part.isEmpty()) continue;
            int sp = part.indexOf(' ');
            if (sp < 0) result.put(part, "");
            else result.put(part.substring(0, sp), part.substring(sp + 1).trim());
        }
        return result;
    }

    private static int[] parseBbox(String value) {
        if (value == null) return null;
        String[] parts = value.trim().split("\\s+");
        if (parts.length != 4) return null;
        int[] box = new int[4];
        try {
            for (int i = 0; i < 4; i++) box[i] = Integer.parseInt(parts[i]);
            return box;
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private static StyleHints parseHints(Map<String, String> props) {
        String family = props.get("x_font");
        Double size = null;
        if (props.containsKey("x_fsize")) {
            double parsed = parseDouble(props.get("x_fsize"), Double.NaN);
            if (Double.isFinite(parsed) && parsed > 0) size = parsed;
        }
        Boolean bold = boolOf(props.get("x_bold"));
        Boolean italic = boolOf(props.get("x_italic"));

        if (family == null && size == null && bold == null && italic == null) {
            return StyleHints.NONE;
        }
        return new StyleHints(family, size, bold, italic);
    }

    private static Boolean boolOf(String value) {
        if (value == null) return null;
        return value.equals("1") || value.equalsIgnoreCase("true");
    }

    private static double parseDouble(String value, double fallback) {
        if (value == null) return fallback;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException error) {
            return fallback;
        }
    }
}