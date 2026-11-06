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
 * Builds exactly one {@code Item("text", …)} per visual line.
 *
 * <p><b>Typography invariant.</b> Inside a visual line there is at most one
 * rendered size:
 * <ul>
 *   <li><b>Multi-run HTML</b> (mixed colour / bold / italic / family) —
 *       every {@code <span>} carries {@code font-size = lineFontSize}. The
 *       per-run pixel estimate is deliberately not used here: it differs from
 *       the line average by 15–25 % even when the font is uniform (x-height
 *       words vs. words with ascenders/descenders), and that is what produced
 *       the "jumping letters" artefact.</li>
 *   <li><b>Single-run plain text</b> — the analyzer's own estimate
 *       ({@code style.fontSize()}) is used. There is a single span, so no
 *       jumping is possible; the pixel estimate is the more accurate of the
 *       two for a representative word.</li>
 *   <li><b>No style data</b> — the plain-Tesseract formula
 *       {@code (bottom − top) × 1.05}.</li>
 * </ul>
 */
@Component
public class TextElementBuilder {

    private final StyledRunSplitter runSplitter;

    public TextElementBuilder(StyledRunSplitter runSplitter) {
        this.runSplitter = runSplitter;
    }

    public Item build(BufferedImage image, List<Word> line,
                      int canvasWidth, int canvasHeight) {
        if (line.isEmpty()) return null;

        int top = line.stream().mapToInt(Word::y).min().orElse(0);
        int bottom = line.stream()
                .mapToInt(w -> w.y() + w.height())
                .max()
                .orElse(top);
        int left = line.stream().mapToInt(Word::x).min().orElse(0);
        int right = line.stream()
                .mapToInt(w -> w.x() + w.width())
                .max()
                .orElse(canvasWidth);

        double lineFontSize = Math.max(8.0, (bottom - top) * 1.05);

        List<StyledRun> runs;
        try {
            runs = runSplitter.split(image, line);
        } catch (RuntimeException error) {
            runs = List.of();
        }

        int boxX = left;
        int boxY = Math.max(0, top - 2);
        int boxW = Math.min(canvasWidth - left, right - left + 6);
        int boxH = Math.min(canvasHeight - top, bottom - top + 6);

        Map<String, Object> properties;
        if (runs.isEmpty()) {
            properties = plainProperties(line, lineFontSize);
        } else if (runs.size() == 1) {
            properties = singleRunProperties(runs.get(0), line);
        } else {
            properties = multiRunProperties(runs, line, lineFontSize);
        }

        return new Item("text", boxX, boxY, boxW, boxH, properties);
    }

    /* ============================================================
       Plain text — no style analysis available.
       ============================================================ */

    private static Map<String, Object> plainProperties(List<Word> line, double lineFontSize) {
        var properties = new LinkedHashMap<String, Object>();
        properties.put("text", String.join(" ",
                line.stream().map(Word::text).toList()));
        properties.put("fontSize", lineFontSize);
        properties.put("fill", "#202020");
        properties.put("fontFamily", "Arial");
        properties.put("fontStyle", "normal");
        properties.put("textDecoration", "");
        properties.put("richText", false);
        properties.put("confidence",
                line.stream().mapToDouble(Word::confidence).average().orElse(0));
        return properties;
    }

    /* ============================================================
       Single-style line — one span, one size; use the analyzer's estimate.
       ============================================================ */

    private static Map<String, Object> singleRunProperties(
            StyledRun run, List<Word> line) {
        TextStyle style = run.style();
        var properties = new LinkedHashMap<String, Object>();
        properties.put("text", String.join(" ",
                line.stream().map(Word::text).toList()));
        properties.put("fontSize", style.fontSize());
        properties.put("fill", style.color());
        properties.put("fontFamily", style.fontFamily());
        properties.put("fontStyle", fontStyleOf(style));
        properties.put("textDecoration", decorationOf(style));
        properties.put("richText", false);
        properties.put("confidence",
                line.stream().mapToDouble(Word::confidence).average().orElse(0));
        return properties;
    }

    /* ============================================================
       Mixed-style line — HTML with per-span style, uniform line-level size.
       ============================================================ */

    private static Map<String, Object> multiRunProperties(
            List<StyledRun> runs, List<Word> line, double lineFontSize) {
        double confidence = line.stream()
                .mapToDouble(Word::confidence).average().orElse(0);

        StyledRun first = runs.get(0);
        String plainText = String.join(" ",
                runs.stream().map(r -> r.word().text()).toList());

        var properties = new LinkedHashMap<String, Object>();
        properties.put("text", plainText);
        properties.put("html", buildHtml(runs, lineFontSize));
        properties.put("richText", true);
        properties.put("fontSize", lineFontSize);
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

    private static String buildHtml(List<StyledRun> runs, double lineFontSize) {
        StringBuilder html = new StringBuilder("<div>");
        boolean firstRun = true;
        for (StyledRun run : runs) {
            TextStyle s = run.style();
            if (!firstRun) html.append(' ');
            firstRun = false;

            // Line base font-size for every span — no per-span size drift.
            html.append("<span style=\"")
                    .append("color:").append(s.color()).append(';')
                    .append("font-size:").append(Math.round(lineFontSize)).append("px;")
                    .append("font-family:'").append(XmlEscape.attr(s.fontFamily())).append("';");
            if (s.bold()) html.append("font-weight:700;");
            if (s.italic()) html.append("font-style:italic;");
            String decoration = decorationOf(s);
            if (!decoration.isEmpty()) {
                html.append("text-decoration:").append(decoration).append(';');
            }
            html.append("\">")
                    .append(XmlEscape.escape(run.word().text()))
                    .append("</span>");
        }
        html.append("</div>");
        return html.toString();
    }
}