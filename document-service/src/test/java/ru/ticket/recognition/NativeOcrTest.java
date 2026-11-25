package ru.ticket.recognition;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.ticket.recognition.tesseract.TesseractProcessRunner;

/**
 * Интеграционный тест: проверяет, что Tesseract действительно установлен
 * и корректно распознает простой текст.
 *
 * <p><b>Почему assumeTrue.</b> Этот тест зависит от внешнего бинарника
 * {@code tesseract}, которого может не быть в CI-окружении или на машине
 * разработчика. JUnit Assumption позволяет корректно пропустить тест
 * (пометить как Skipped), а не ронять сборку с ошибкой.
 */
class NativeOcrTest {

  /**
   * Проверяет доступность tesseract в PATH.
   */
  private static boolean isTesseractInstalled() {
    try {
      Process p = new ProcessBuilder("tesseract", "--version").start();
      return p.waitFor() == 0;
    } catch (Exception e) {
      return false;
    }
  }

  @Test
  void tesseractRecognizesSimpleText() {
    assumeTrue(isTesseractInstalled(),
            "Tesseract is not installed, skipping integration test");

    // 1. Рендерим простую картинку с текстом
    BufferedImage image = new BufferedImage(300, 100, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, 300, 100);
    g.setColor(Color.BLACK);
    g.setFont(new Font("Arial", Font.PLAIN, 40));
    g.drawString("Hello OCR", 20, 60);
    g.dispose();

    // 2. Запускаем распознавание через реальный движок
    OcrEngine engine = new TesseractOcrEngine(new TesseractProcessRunner("tesseract"));
    List<OcrEngine.Word> words = engine.recognize(image, "eng");

    // 3. Проверяем, что хотя бы одно слово найдено
    assertFalse(words.isEmpty(), "Tesseract должен найти хотя бы одно слово");
  }
}