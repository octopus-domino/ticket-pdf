package ru.ticket.recognition;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Порт OCR-движка: «картинка + язык → список слов с геометрией».
 *
 * <p>Реализации: {@link TesseractOcrEngine} (TSV, стабильный),
 * {@code ru.ticket.recognition.hocr.TesseractHocrEngine} (hOCR, с
 * подсказками шрифта). Оба работают параллельно — их сливает
 * {@code OcrResultFusion}.
 */
public interface OcrEngine {

  /**
   * Опциональные подсказки от движка OCR. Все поля nullable —
   * отсутствие означает «решай сам по картинке».
   */
  record StyleHints(
          String fontFamily,
          Double fontSize,
          Boolean bold,
          Boolean italic) {
    public static final StyleHints NONE = new StyleHints(null, null, null, null);
  }

  /**
   * Одно слово OCR-прохода.
   *
   * @param line идентификатор строки в пределах прохода; слова с
   *             одинаковым {@code line} собираются в один
   *             {@code Item("text", ...)} в шаблоне
   */
  record Word(
          String text,
          int x, int y, int width, int height,
          double confidence,
          String line,
          StyleHints hints) {

    /** Совместимый конструктор для движков без style-подсказок. */
    public Word(String text, int x, int y, int width, int height,
                double confidence, String line) {
      this(text, x, y, width, height, confidence, line, StyleHints.NONE);
    }
  }

  List<Word> recognize(BufferedImage image, String language);
}