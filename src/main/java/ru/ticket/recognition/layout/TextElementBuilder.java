package ru.ticket.recognition.layout;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import ru.ticket.recognition.OcrEngine.Word;
import ru.ticket.recognition.RecognitionDraft.Item;
import ru.ticket.recognition.style.StyledRun;
import ru.ticket.recognition.style.StyledRunSplitter;
import ru.ticket.recognition.style.TextStyle;
import ru.ticket.util.XmlEscape;

/**
 * Собирает {@code Item("text", ...)} из одной OCR-строки: плоский
 * {@code TextElement} (один стиль) или rich HTML (несколько стилей).
 *
 * <p><b>Почему rich HTML, а не несколько TextElement.</b> Rich HTML
 * сохраняет целостность строки: двигаешь строку — все фрагменты едут
 * вместе. Несколько TextElement «рассыпались» бы.
 */
@Component
public class TextElementBuilder {

    private final StyledRunSplitter runSplitter;

    public TextElementBuilder(StyledRunSplitter runSplitter) {
        this.runSplitter = runSplitter;
    }

    /**
     * @return {@link Item} с типом {@code text} или {@code null}, если
     *         строка пуста
     */
    public Item build(BufferedImage image, List<Word> line,
                      int canvasWidth, int canvasHeight) {
        if (line.isEmpty()) return null;

        List<StyledRun> runs;
        try {
            runs = runSplitter.split(image, line);
        } catch (RuntimeException error) {
            runs = List.of();
        }

        int x = line.stream().mapToInt(Word::x).min().orElse(0);
        int y = line.stream().mapToInt(Word::y).min().orElse(0);
        int right = line.stream().mapToInt(w -> w.x() + w.width()).max().orElse(canvasWidth);
        int bottom = line.stream().mapToInt(w -> w.y() + w.height()).max().orElse(canvasHeight);

        int boxX = x;
        int boxY = Math.max(0, y - 2);
        int boxW = Math.min(canvasWidth - x, right - x + 6);
        int boxH = Math.min(canvasHeight - y, bottom - y + 6);

        Map<String, Object> properties = runs.isEmpty()
                ? plainProperties(line)
                : propertiesFromRuns(runs, line);

        return new Item("text", boxX, boxY, boxW, boxH, properties);
    }

    private static Map<String, Object> plainProperties(List<Word> line) {
        int top = line.stream().mapToInt(Word::y).min().orElse(0);
        int bottom = line.stream().mapToInt(w -> w.y() + w.height()).max().orElse(top);

        var properties = new LinkedHashMap<String, Object>();
        properties.put("text", String.join(" ", line.stream().map(Word::text).toList()));
        properties.put("fontSize", Math.max(8, (bottom - top) * 1.05));
        properties.put("fill", "#202020");
        properties.put("fontFamily", "Arial");
        properties.put("fontStyle", "normal");
        properties.put("textDecoration", "");
        properties.put("richText", false);
        properties.put("confidence",
                line.stream().mapToDouble(Word::confidence).average().orElse(0));
        return properties;
    }

    private static Map<String, Object> propertiesFromRuns(
            List<StyledRun> runs, List<Word> line) {

        double confidence = line.stream()
                .mapToDouble(Word::confidence).average().orElse(0);

        if (runs.size() == 1) {
            StyledRun only = runs.get(0);
            TextStyle style = only.style();

            var properties = new LinkedHashMap<String, Object>();
            properties.put("text", only.word().text());
            properties.put("fontSize", style.fontSize());
            properties.put("fill", style.color());
            properties.put("fontFamily", style.fontFamily());
            properties.put("fontStyle", fontStyleOf(style));
            properties.put("textDecoration", decorationOf(style));
            properties.put("richText", false);
            properties.put("confidence", confidence);
            return properties;
        }

        StyledRun first = runs.get(0);
        String plainText = String.join(" ",
                runs.stream().map(r -> r.word().text()).toList());

        var properties = new LinkedHashMap<String, Object>();
        properties.put("text", plainText);
        properties.put("html", buildHtml(runs));
        properties.put("richText", true);
        properties.put("fontSize", first.style().fontSize());
        properties.put("fill", first.style().color());
        properties.put("fontFamily", first.style().fontFamily());
        properties.put("fontStyle", fontStyleOf(first.style()));
        properties.put("textDecoration", decorationOf(first.style()));
        properties.put("confidence", confidence);
        return properties;
    }

    private static String fontStyleOf(TextStyle s) {
        if (s.bold() && s.italic()) return "bold italic";
        if (s.bold()) return "bold";
        if (s.italic()) return "italic";
        return "normal";
    }

    private static String decorationOf(TextStyle s) {
        StringBuilder sb = new StringBuilder();
        if (s.underline()) sb.append("underline");
        if (s.strike()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append("line-through");
        }
        return sb.toString();
    }

    private static String buildHtml(List<StyledRun> runs) {
        StringBuilder html = new StringBuilder("<div>");
        for (StyledRun run : runs) {
            TextStyle s = run.style();
            html.append("<span style=\"")
                    .append("color:").append(s.color()).append(';')
                    .append("font-size:").append(Math.round(s.fontSize())).append("px;")
                    .append("font-family:'").append(XmlEscape.attr(s.fontFamily())).append("';");
            if (s.bold()) html.append("font-weight:700;");
            if (s.italic()) html.append("font-style:italic;");
            String decoration = decorationOf(s);
            if (!decoration.isEmpty()) {
                html.append("text-decoration:").append(decoration).append(';');
            }
            html.append("\">")
                    .append(XmlEscape.escape(run.word().text()))
                    .append("</span> ");
        }
        html.append("</div>");
        return html.toString();
    }
}