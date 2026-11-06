package ru.ticket.recognition.style;

import java.awt.image.BufferedImage;
import java.util.List;
import org.springframework.stereotype.Component;
import ru.ticket.recognition.InkStyleSampler;
import ru.ticket.recognition.OcrEngine.StyleHints;
import ru.ticket.recognition.OcrEngine.Word;

/**
 * Combines ink-based style analysis with optional OCR hints.
 *
 * <p><b>Precedence — explicit hints win.</b> hOCR's {@code x_font},
 * {@code x_fsize}, {@code x_bold}, {@code x_italic} are produced by
 * Tesseract's own font-info pass and, when present, are generally more
 * accurate than pixel heuristics (which fire spuriously on serif rendering,
 * anti-aliasing and small skew). So:
 * <ul>
 *   <li>pixel analysis provides the baseline</li>
 *   <li>a non-{@code null} hint overrides the corresponding baseline value</li>
 *   <li>the analyzer never merges — a word with no hints keeps its pixel
 *       result untouched</li>
 * </ul>
 * This is the same contract as before this change; the layout regression was
 * upstream in fusion (lost line ids) and downstream in HTML serialisation
 * (per-span font-size), not in this precedence rule.
 */
@Component
public class CompositeTextStyleAnalyzer implements TextStyleAnalyzer {

    private final FontMatcher fontMatcher;

    public CompositeTextStyleAnalyzer(FontMatcher fontMatcher) {
        this.fontMatcher = fontMatcher;
    }

    @Override
    public TextStyle analyze(BufferedImage image, Word word) {
        VisualFontProfile profile = fontMatcher.profile(word, image);

        double fontSize = AwtFontMatcher.estimateFontSize(word, profile);
        boolean bold = profile.strokeRatio() > 0.085;
        boolean italic = profile.italicSkew() > 0.08;
        boolean underline = StrokeAnalyzer.hasUnderline(
                image, word.x(), word.y(), word.width(), word.height());
        boolean strike = StrokeAnalyzer.hasStrikethrough(
                image, word.x(), word.y(), word.width(), word.height());

        String color = InkStyleSampler.textColor(image, List.of(word));
        String family = fontMatcher.match(word, profile);

        StyleHints hints = word.hints() == null ? StyleHints.NONE : word.hints();
        if (hints.fontSize() != null) fontSize = hints.fontSize();
        if (hints.bold() != null) bold = hints.bold();
        if (hints.italic() != null) italic = hints.italic();
        if (hints.fontFamily() != null && !hints.fontFamily().isBlank()) {
            family = hints.fontFamily();
        }

        return new TextStyle(color, fontSize, bold, italic, underline, strike, family);
    }
}