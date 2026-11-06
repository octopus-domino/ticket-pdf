package ru.ticket.recognition.style;

import java.awt.image.BufferedImage;
import java.util.List;
import org.springframework.stereotype.Component;
import ru.ticket.recognition.InkStyleSampler;
import ru.ticket.recognition.OcrEngine.StyleHints;
import ru.ticket.recognition.OcrEngine.Word;

/**
 * Комбинирует визуальный анализ растра с подсказками OCR.
 *
 * <p>Подсказки hOCR/API имеют приоритет — это осознанное подтверждение
 * движка. Когда их нет — визуальный анализ полностью определяет стиль.
 * Цвет и декорации всегда берутся из пикселей: Tesseract их не отдаёт.
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