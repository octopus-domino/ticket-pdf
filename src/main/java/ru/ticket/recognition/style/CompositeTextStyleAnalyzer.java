package ru.ticket.recognition.style;

import java.awt.image.BufferedImage;
import java.util.List;
import org.springframework.stereotype.Component;
import ru.ticket.recognition.InkStyleSampler;
import ru.ticket.recognition.OcrEngine.StyleHints;
import ru.ticket.recognition.OcrEngine.Word;

/**
 * Combines ink-based style analysis (bold/italic/underline/colour/family)
 * with optional OCR hints.
 *
 * <p><b>Why pixels win over hints.</b> Tesseract's {@code x_bold},
 * {@code x_italic} and {@code x_fsize} are classifier guesses trained on
 * generic corpora. In practice they fire spuriously on serif rendering,
 * anti-aliasing, and small skew — a single false positive flips
 * {@link InkBasedRunSplitter} into creating a run boundary that fragments the
 * line. Ink analysis ({@link StrokeAnalyzer}, {@link InkStyleSampler}) works
 * on the actual glyph bitmaps and is stable across neighbouring words, so it
 * is the primary signal for everything that can be measured from pixels.
 *
 * <p>Hints are retained only as a <em>font-family fallback</em>: when pixel
 * matching returns the default {@code Arial} but hOCR provides a concrete
 * family name (e.g. via {@code hocr_font_info=1}), the hint is accepted. Bold,
 * italic, size and underline from hints are ignored — pixel analysis is more
 * accurate for those.
 */
@Component
public class CompositeTextStyleAnalyzer implements TextStyleAnalyzer {

    private static final String DEFAULT_FAMILY = "Arial";

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

        // Family: adopt the hOCR hint only if pixel matching was inconclusive
        // (fell back to Arial) and the hint names a different family.
        if (DEFAULT_FAMILY.equals(family)
                && hints.fontFamily() != null
                && !hints.fontFamily().isBlank()
                && !hints.fontFamily().equalsIgnoreCase(DEFAULT_FAMILY)) {
            family = hints.fontFamily();
        }
        // bold/italic/underline/size from hints are intentionally ignored.

        return new TextStyle(color, fontSize, bold, italic, underline, strike, family);
    }
}