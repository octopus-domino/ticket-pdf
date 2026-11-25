package ru.ticket.recognition.style;

import java.awt.image.BufferedImage;
import ru.ticket.recognition.OcrEngine.Word;

/** Извлекает стиль слова из растра (и опциональных подсказок OCR). */
public interface TextStyleAnalyzer {
    TextStyle analyze(BufferedImage image, Word word);
}