package ru.ticket.recognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.ticket.recognition.ensemble.DualEngineOcrRunner;
import ru.ticket.recognition.ensemble.OcrResultFusion;
import ru.ticket.recognition.layout.ArtworkDetector;
import ru.ticket.recognition.layout.RuledTableDetector;
import ru.ticket.recognition.layout.TextElementBuilder;
import ru.ticket.recognition.shape.ContourShapeRecognizer;
import ru.ticket.recognition.style.StyledRun;
import ru.ticket.recognition.style.TextStyle;

class RecognitionServiceTest {

  private final PageLayoutAnalyzer analyzer = new PageLayoutAnalyzer(
          new RuledTableDetector(),
          new TextElementBuilder((img, line) -> List.of(new StyledRun(line.get(0), TextStyle.defaultFor("#000000", 14)))),
          new ArtworkDetector()
  );

  @Test
  void imageEncodingProducesValidDataUrl() {
    BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    g.setColor(Color.RED);
    g.fillRect(0, 0, 10, 10);
    g.dispose();

    // Проверяем замену старому PageLayoutAnalyzer.png()
    String dataUrl = ImageEncoding.pngDataUrl(image);

    assertNotNull(dataUrl);
    assertEquals(true, dataUrl.startsWith("data:image/png;base64,"));
  }

  @Test
  void recognitionPipelineCombinesTextAndShapes() {
    BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, 200, 200);

    // Рисуем текст и фигуру
    g.setColor(Color.BLACK);
    g.drawString("Test", 10, 20);
    g.fillRect(50, 50, 50, 50);
    g.dispose();

    // Создаем мок слова (как будто OCR его нашел)
    var word = new OcrEngine.Word("Test", 10, 10, 40, 20, 95, "L1");

    // Запускаем оркестратор (PageLayoutAnalyzer)
    var draft = analyzer.analyze(image, List.of(word), false);
    assertNotNull(draft);

    // Запускаем поиск фигур (ContourShapeRecognizer)
    var shapeRecognizer = new ContourShapeRecognizer();
    var mask = ru.ticket.recognition.layout.TextMask.fromItems(200, 200, draft.elements());
    var shapes = shapeRecognizer.recognize(image, mask);

    assertEquals(true, shapes.size() >= 1, "Должен найти хотя бы один квадрат");
  }
}