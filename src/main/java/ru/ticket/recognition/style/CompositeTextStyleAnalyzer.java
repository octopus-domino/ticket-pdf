package ru.ticket.recognition.style;

import java.awt.image.BufferedImage;
import java.util.List;
import org.springframework.stereotype.Component;
import ru.ticket.recognition.InkStyleSampler;
import ru.ticket.recognition.OcrEngine.StyleHints;
import ru.ticket.recognition.OcrEngine.Word;

@Component
public class CompositeTextStyleAnalyzer implements TextStyleAnalyzer {

    private final FontMatcher fontMatcher;

    public CompositeTextStyleAnalyzer(FontMatcher fontMatcher) {
        this.fontMatcher = fontMatcher;
    }

    @Override
    public TextStyle analyze(BufferedImage image, Word word) {
        VisualFontProfile profile = fontMatcher.profile(word, image);

        FontMatch match = fontMatcher.resolve(word, image, profile);
        // Typeface confidence and size are independent: even an ambiguous family
        // has measurable glyph bounds. The whole OCR box includes leading.
        double measuredSize = Double.isFinite(match.size()) ? match.size() : word.height();
        double fontSize = Math.max(8, Math.min(word.height() * 1.4, measuredSize));
        boolean bold = match.reliable() && match.bold();
        boolean italic = match.reliable() && match.italic();
        boolean underline = StrokeAnalyzer.hasUnderline(
                image, word.x(), word.y(), word.width(), word.height());
        boolean strike = StrokeAnalyzer.hasStrikethrough(
                image, word.x(), word.y(), word.width(), word.height());

        String color = InkStyleSampler.textColor(image, List.of(word));
        String family = match.reliable() ? match.family() : "Arial";

        StyleHints hints = word.hints() == null ? StyleHints.NONE : word.hints();

        // hOCR point sizes have no reliable pixel conversion without source DPI.
        // Uncertain font-family guesses must not change the stable TSV layout.
        if (!match.reliable() && hints.bold() != null) bold = hints.bold();
        if (!match.reliable() && hints.italic() != null) italic = hints.italic();

        return new TextStyle(color, fontSize, bold, italic, underline, strike, family);
    }
}
