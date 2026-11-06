package ru.ticket.recognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import ru.ticket.recognition.layout.ArtworkDetector;
import ru.ticket.recognition.layout.RuledTableDetector;
import ru.ticket.recognition.layout.TextElementBuilder;
import ru.ticket.recognition.style.StyledRun;
import ru.ticket.recognition.style.TextStyle;

class ArtworkBackgroundTest {

  /**
   * Собираем PageLayoutAnalyzer вручную с реальными зависимостями.
   * Стилевой сплиттер делаем примитивным (всегда один run), чтобы не тащить
   * сюда AwtTextLayoutService и не зависеть от системных шрифтов.
   */
  private final PageLayoutAnalyzer analyzer = new PageLayoutAnalyzer(
          new RuledTableDetector(),
          new TextElementBuilder((image, line) ->
                  List.of(new StyledRun(line.get(0), TextStyle.defaultFor("#000000", 14)))),
          new ArtworkDetector()
  );

  @Test
  void rasterArtworkKeepsItsColorsAndRealWhiteDetailsAfterOcrTextExtraction() throws Exception {
    var source = filled(300, 180, Color.BLUE);
    var graphics = source.createGraphics();
    graphics.setColor(Color.ORANGE);
    graphics.fillRect(150, 0, 150, 180);
    graphics.setColor(Color.WHITE);
    graphics.fillRect(0, 140, 300, 2);
    graphics.setColor(Color.BLACK);
    graphics.fillRect(40, 40, 100, 50);
    graphics.dispose();

    var words = List.of(new OcrEngine.Word("Hello", 40, 40, 100, 50, 95, "one"));
    var draft = analyzer.analyze(source, words);

    var artwork = draft.elements().stream()
            .filter(item -> item.type().equals("image"))
            .findFirst().orElseThrow();

    String dataUrl = artwork.properties().get("src").toString();
    byte[] png = Base64.getDecoder().decode(dataUrl.substring(dataUrl.indexOf(',') + 1));
    var crop = ImageIO.read(new ByteArrayInputStream(png));

    int left = (int) artwork.x();
    int top = (int) artwork.y();

    assertEquals(Color.BLUE.getRGB(), crop.getRGB(80 - left, 60 - top));
    assertEquals(Color.ORANGE.getRGB(), crop.getRGB(220 - left, 60 - top));
    assertEquals(Color.WHITE.getRGB(), crop.getRGB(80 - left, 140 - top),
            "A white detail already present in the source must remain");

    assertTrue(draft.elements().stream().anyMatch(item -> item.type().equals("text")));
  }

  @Test
  void solidDarkPanelEdgesAreNotInventedEditableRules() {
    var source = filled(300, 180, Color.WHITE);
    var graphics = source.createGraphics();
    graphics.setColor(new Color(24, 60, 57));
    graphics.fillRect(24, 36, 240, 108);
    graphics.dispose();

    // Проверяем, что темная панель не превращается в линии
    var draft = analyzer.analyze(source, List.of());
    assertTrue(draft.elements().stream().noneMatch(item -> "line".equals(item.type())));
  }

  private static BufferedImage filled(int w, int h, Color color) {
    BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    g.setColor(color);
    g.fillRect(0, 0, w, h);
    g.dispose();
    return image;
  }
}