package ru.ticket.recognition;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import ru.ticket.common.ApiException;
import ru.ticket.recognition.tesseract.TesseractProcessRunner;

/**
 * TSV-проход Tesseract: координаты, confidence, группировка по строкам.
 *
 * <p><b>SRP.</b> Только «вызвать tesseract в режиме TSV и разобрать вывод».
 * Процесс и парсинг hOCR — в других классах.
 *
 * <p><b>Почему TSV.</b> Стабильный формат, поддерживаемый всеми версиями
 * Tesseract. Поля шрифта здесь не запрашиваются сознательно — hOCR
 * известен ненадёжными {@code x_font}.
 */
@Component
public class TesseractOcrEngine implements OcrEngine {

  private static final long MAX_OUTPUT_BYTES = 2_000_000L;

  private final TesseractProcessRunner runner;

  public TesseractOcrEngine(TesseractProcessRunner runner) {
    this.runner = runner;
  }

  @Override
  public List<Word> recognize(BufferedImage image, String language) {
    var request = new TesseractProcessRunner.Request(
            List.of("-l", language, "--psm", "3", "tsv"),
            MAX_OUTPUT_BYTES);
    return parse(runner.run(image, request));
  }

  /**
   * TSV: строки с {@code level != 5} — это страницы, блоки, параграфы,
   * строки. Только {@code level == 5} — слова с bbox и confidence.
   *
   * <p>Package-private ради юнит-тестов без запуска процесса.
   */
  static List<Word> parse(String tsv) {
    var result = new ArrayList<Word>();
    for (String row : tsv.split("\\R")) {
      String[] cells = row.split("\t", 12);
      if (cells.length != 12 || !cells[0].equals("5") || cells[11].isBlank()) continue;
      try {
        int x = Integer.parseInt(cells[6]);
        int y = Integer.parseInt(cells[7]);
        int w = Integer.parseInt(cells[8]);
        int h = Integer.parseInt(cells[9]);
        if (x < 0 || y < 0 || w <= 0 || h <= 0) continue;

        result.add(new Word(
                cells[11].strip(), x, y, w, h,
                Double.parseDouble(cells[10]),
                cells[1] + ":" + cells[2] + ":" + cells[3] + ":" + cells[4]));

        if (result.size() > 1200) {
          throw ApiException.badRequest("recognition.too_complex");
        }
      } catch (NumberFormatException ignored) {
        // битые строки не считаем текстом
      }
    }
    return result;
  }
}