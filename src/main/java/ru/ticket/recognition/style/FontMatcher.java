package ru.ticket.recognition.style;

import java.awt.image.BufferedImage;
import ru.ticket.recognition.OcrEngine.Word;

/** Считает визуальный профиль слова и подбирает ближайший шрифт. */
public interface FontMatcher {
    VisualFontProfile profile(Word word, BufferedImage image);
    String match(Word word, VisualFontProfile profile);
}