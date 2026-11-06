package ru.ticket.recognition.style;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import ru.ticket.recognition.OcrEngine.Word;

/**
 * Splits an OCR line into styled runs using pixel analysis.
 *
 * <p><b>Font size is not a split criterion.</b> Size is computed once at the
 * line level by {@code TextElementBuilder} and never varies between spans of
 * the same visual line. Per-word pixel estimates differ from the line average
 * by 15–25 % even when the underlying font is uniform (x-height words vs
 * words with ascenders/descenders), so treating size as a split trigger
 * fragments the line into many runs with slightly different sizes — the
 * "jumping letters" artefact. Removing it here is intentional.
 *
 * <p>What <em>is</em> a split criterion — robust pixel signals that survive
 * across neighbouring words:
 * <ul>
 *   <li>foreground colour change</li>
 *   <li>bold ↔ regular</li>
 *   <li>italic ↔ upright</li>
 *   <li>underline / strike toggles</li>
 *   <li>font-family change</li>
 * </ul>
 * These either represent genuine inline markup (a bold word inside a
 * sentence, a link-coloured fragment) or are physically improbable as noise
 * across adjacent glyphs.
 */
@Component
public class InkBasedRunSplitter implements StyledRunSplitter {

    private final TextStyleAnalyzer styleAnalyzer;

    public InkBasedRunSplitter(TextStyleAnalyzer styleAnalyzer) {
        this.styleAnalyzer = styleAnalyzer;
    }

    @Override
    public List<StyledRun> split(BufferedImage image, List<Word> line) {
        List<StyledRun> result = new ArrayList<>();
        if (line == null || line.isEmpty()) return result;

        List<Word> sorted = new ArrayList<>(line);
        sorted.sort(Comparator.comparingInt(Word::x));

        Word currentWord = null;
        TextStyle currentStyle = null;

        for (Word word : sorted) {
            TextStyle style;
            try {
                style = styleAnalyzer.analyze(image, word);
            } catch (RuntimeException error) {
                style = TextStyle.defaultFor("#202020", Math.max(8, word.height() * 0.9));
            }

            if (currentWord == null || differs(currentStyle, style)) {
                if (currentWord != null) {
                    result.add(new StyledRun(currentWord, currentStyle));
                }
                currentWord = word;
                currentStyle = style;
            } else {
                currentWord = mergeWords(currentWord, word);
            }
        }
        if (currentWord != null) {
            result.add(new StyledRun(currentWord, currentStyle));
        }
        return result;
    }

    /** Split criterion. Font size is deliberately absent — see class javadoc. */
    private static boolean differs(TextStyle a, TextStyle b) {
        if (a == null || b == null) return true;
        if (!a.color().equalsIgnoreCase(b.color())) return true;
        if (a.bold() != b.bold()) return true;
        if (a.italic() != b.italic()) return true;
        if (a.underline() != b.underline()) return true;
        if (a.strike() != b.strike()) return true;
        return !a.fontFamily().equals(b.fontFamily());
    }

    private static Word mergeWords(Word a, Word b) {
        int x = Math.min(a.x(), b.x());
        int y = Math.min(a.y(), b.y());
        int right = Math.max(a.x() + a.width(), b.x() + b.width());
        int bottom = Math.max(a.y() + a.height(), b.y() + b.height());
        String text = a.text() + " " + b.text();
        double conf = (a.confidence() + b.confidence()) / 2.0;
        // Merged run keeps the original line id — it is what drives layout.
        return new Word(text, x, y, right - x, bottom - y, conf, a.line());
    }
}