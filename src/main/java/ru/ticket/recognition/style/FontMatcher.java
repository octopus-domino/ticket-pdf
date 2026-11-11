package ru.ticket.recognition.style;

import java.awt.image.BufferedImage;
import ru.ticket.recognition.OcrEngine.Word;

/** Считает визуальный профиль слова и подбирает ближайший шрифт. */
public interface FontMatcher {
    VisualFontProfile profile(Word word, BufferedImage image);
    String match(Word word, VisualFontProfile profile);
    default FontMatch resolve(Word word, BufferedImage image, VisualFontProfile profile) {
        return new FontMatch(match(word, profile), profile.estimateSize(word.height()),
                profile.strokeRatio() > 0.085, profile.italicSkew() > 0.08);
    }
}
