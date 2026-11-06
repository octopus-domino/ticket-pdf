package ru.ticket.recognition;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.ticket.recognition.tesseract.TesseractProcessRunner;

class NativeOcrTest {

  @Test
  void tesseractRecognizesSimpleText() {
    // 1. Готовим картинку с текстом
    BufferedImage image = new BufferedImage(300, 100, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, 300, 100);
    g.setColor(Color.BLACK);
    g.setFont(new Font("Arial", Font.PLAIN, 40));
    g.drawString("Hello OCR", 20, 60);
    g.dispose();

    // 2. Создаем движок с правильным конструктором (через TesseractProcessRunner)
    OcrEngine engine = new TesseractOcrEngine(new TesseractProcessRunner("tesseract"));

    // 3. Распознаем (используем "eng" для простоты, чтобы не зависеть от установленных языков)
    List<OcrEngine.Word> words = engine.recognize(image, "eng");

    // 4. Проверяем, что текст распознан (Tesseract может выдать "Hello", "OCR" или "Hello OCR" в зависимости от версии)
    assertFalse(words.isEmpty(), "Tesseract должен найти хотя бы одно слово");
  }
}