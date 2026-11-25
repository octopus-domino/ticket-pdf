package ru.ticket.recognition.style;

import java.awt.image.BufferedImage;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.ticket.recognition.OcrEngine.Word;
import ru.ticket.text.TextLayoutService;

/**
 * Подбирает установленный шрифт, минимизируя разницу между шириной
 * отрендеренного слова и шириной наблюдаемого бокса. Плюс лёгкий штраф
 * за несоответствие толщины штриха — так serif/sans/mono корректно
 * разделяются.
 */
@Component
public class AwtFontMatcher implements FontMatcher {

    public static final List<String> DEFAULT_CANDIDATES =
            List.of("Arial", "Times New Roman", "Georgia", "Verdana", "Courier New");

    private final TextLayoutService textLayout;
    private final List<String> candidates;

    @Autowired
    public AwtFontMatcher(TextLayoutService textLayout) {
        this(textLayout, DEFAULT_CANDIDATES);
    }

    AwtFontMatcher(TextLayoutService textLayout, List<String> candidates) {
        this.textLayout = textLayout;
        this.candidates = candidates.stream().filter(ru.ticket.text.FontFamilyResolver::available).toList();
    }

    @Override
    public VisualFontProfile profile(Word word, BufferedImage image) {
        int x = word.x(), y = word.y(), w = word.width(), h = word.height();
        double stroke = StrokeAnalyzer.medianHorizontalRunLength(image, x, y, w, h);
        double xh = StrokeAnalyzer.xHeight(image, x, y, w, h);
        double skew = StrokeAnalyzer.italicSkew(image, x, y, w, h);
        return new VisualFontProfile(
                (double) w / Math.max(1, h),
                stroke / Math.max(1, h),
                xh / Math.max(1, h),
                skew);
    }

    @Override
    public FontMatch resolve(Word word, BufferedImage image, VisualFontProfile profile) {
        return GlyphTemplateMatcher.match(image, word, candidates, FontMatcher.super.resolve(word, image, profile));
    }

    @Override
    public String match(Word word, VisualFontProfile profile) {
        String text = word.text();
        if (text == null || text.isBlank()) return "Arial";

        int fontSize = (int) Math.round(estimateFontSize(word, profile));
        double targetWidth = word.width();

        String best = candidates.isEmpty() ? "Arial" : candidates.get(0);
        double bestErr = Double.MAX_VALUE;

        for (String family : candidates) {
            double rendered;
            try {
                rendered = textLayout.measure(text, family, fontSize, "normal");
            } catch (RuntimeException error) {
                continue;
            }
            double err = Math.abs(rendered - targetWidth) / Math.max(1.0, targetWidth);
            err += Math.abs(expectedStrokeRatio(family) - profile.strokeRatio()) * 0.5;
            if (err < bestErr) {
                bestErr = err;
                best = family;
            }
        }
        return best;
    }

    /**
     * Оценка em-размера по высоте бокса и x-height. Типичное отношение
     * x-height / em ≈ 0.52. Если x-height ≈ высоте бокса — это CAPS: em ≈ box / 0.7.
     */
    static double estimateFontSize(Word word, VisualFontProfile profile) {
        return profile.estimateSize(word.height());
    }

    private static double expectedStrokeRatio(String family) {
        return switch (family) {
            case "Times New Roman", "Georgia" -> 0.055;
            case "Courier New" -> 0.090;
            default -> 0.075;
        };
    }
}
