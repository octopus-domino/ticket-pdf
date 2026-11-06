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

        double visualSize = AwtFontMatcher.estimateFontSize(word, profile);
        double fontSize = visualSize;
        boolean bold = profile.strokeRatio() > 0.085;
        boolean italic = profile.italicSkew() > 0.08;
        boolean underline = StrokeAnalyzer.hasUnderline(
                image, word.x(), word.y(), word.width(), word.height());
        boolean strike = StrokeAnalyzer.hasStrikethrough(
                image, word.x(), word.y(), word.width(), word.height());

        String color = InkStyleSampler.textColor(image, List.of(word));
        String family = fontMatcher.match(word, profile);

        StyleHints hints = word.hints() == null ? StyleHints.NONE : word.hints();

        // Cap the hint font size to prevent huge overflow from bad OCR hints
        if (hints.fontSize() != null) {
            double hintSize = hints.fontSize();
            if (Math.abs(hintSize - visualSize) <= visualSize * 0.5 + 2) {
                fontSize = hintSize;
            }
        }

        if (hints.bold() != null) bold = hints.bold();
        if (hints.italic() != null) italic = hints.italic();
        if (hints.fontFamily() != null && !hints.fontFamily().isBlank()) {
            family = hints.fontFamily();
        }

        return new TextStyle(color, fontSize, bold, italic, underline, strike, family);
    }
}