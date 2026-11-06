package ru.ticket.recognition;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class RecognitionStyleTest {
  private static final Color PAPER = new Color(141, 181, 232);

  @Test void samplesColoredAndWhiteTextAgainstTheActualPaper() {
    for (Color color : List.of(new Color(180, 20, 30), Color.WHITE)) {
      var image = paper();
      var graphics = image.createGraphics();
      graphics.setColor(color);
      graphics.fillRect(30, 30, 40, 20);
      graphics.dispose();
      var words = List.of(new OcrEngine.Word("Title", 30, 30, 40, 20, 95, "one"));
      var draft = new PageLayoutAnalyzer().analyze(image, words);
      var text = draft.elements().stream().filter(e -> e.type().equals("text")).findFirst().orElseThrow();
      assertEquals(String.format("#%06x", color.getRGB() & 0xffffff), text.properties().get("fill"));
    }
  }

  @Test void mergesAllSegmentsAcrossScanRowsAndRemovesTheirFullThicknessFromArtwork() throws Exception {
    var image = paper();
    var graphics = image.createGraphics();
    graphics.setColor(new Color(30, 50, 70));
    graphics.fillRect(20, 90, 100, 4);
    graphics.fillRect(170, 90, 100, 4);
    graphics.dispose();
    var draft = new PageLayoutAnalyzer().analyze(image, List.of());
    var rules = draft.elements().stream().filter(e -> e.type().equals("line")).toList();
    assertEquals(2, rules.size(), "One native line per source stroke, not one per scan row");
    for (var rule : rules) {
      assertEquals(4, rule.properties().get("strokeWidth"));
      assertEquals("#1e3246", rule.properties().get("stroke"));
    }
    for (var artwork : draft.elements()) {
      if (!artwork.type().equals("image")) continue;
      String src = artwork.properties().get("src").toString();
      var crop = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(src.substring(src.indexOf(',') + 1))));
      for (int y = 0; y < crop.getHeight(); y++) for (int x = 0; x < crop.getWidth(); x++)
        assertEquals(PAPER.getRGB(), crop.getRGB(x, y), "Extracted strokes must leave no thin raster remnants");
    }
  }

  @Test void nearbyParallelRulesRemainSeparate() {
    var image = paper();
    var graphics = image.createGraphics();
    graphics.setColor(Color.BLACK);
    graphics.drawLine(20, 60, 270, 60);
    graphics.drawLine(20, 62, 270, 62);
    graphics.dispose();
    assertEquals(2, RuleDetector.detect(image, false).size());
  }

  @Test void flatBackgroundKeepsItsExactColorAndSmallDetailsAreNotFlattened() throws Exception {
    var image = paper();
    var flat = new PageLayoutAnalyzer().analyze(image, List.of());
    assertEquals("#8db5e8", flat.elements().get(0).properties().get("fill"));
    image.setRGB(100, 100, Color.WHITE.getRGB());
    var detailed = new PageLayoutAnalyzer().analyze(image, List.of());
    var artwork = detailed.elements().stream().filter(e -> e.type().equals("image")).findFirst().orElseThrow();
    String src = artwork.properties().get("src").toString();
    var crop = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(src.substring(src.indexOf(',') + 1))));
    assertEquals(Color.WHITE.getRGB(), crop.getRGB(100 - (int)artwork.x(), 100 - (int)artwork.y()));
    assertEquals(PAPER.getRGB(), crop.getRGB(80 - (int)artwork.x(), 80 - (int)artwork.y()));
  }

  private static BufferedImage paper() {
    var image = new BufferedImage(300, 180, BufferedImage.TYPE_INT_RGB);
    var graphics = image.createGraphics();
    graphics.setColor(PAPER);
    graphics.fillRect(0, 0, 300, 180);
    graphics.dispose();
    return image;
  }
}
