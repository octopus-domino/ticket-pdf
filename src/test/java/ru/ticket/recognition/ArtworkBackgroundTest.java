package ru.ticket.recognition;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ArtworkBackgroundTest {
  private static final Color BLUE = new Color(141, 181, 232);
  private static final Color ORANGE = new Color(232, 171, 100);

  @Test
  void removesExtractedInkUsingTheColoredBackgroundWithoutChangingTheSource() {
    var source = filled(300, 180, BLUE);
    var graphics = source.createGraphics();
    graphics.setColor(Color.BLACK);
    graphics.fillRect(60, 50, 140, 40);
    graphics.dispose();
    boolean[] excluded = mask(300, 180, 58, 48, 144, 44);

    var restored = ArtworkBackground.restore(source, excluded, 0, 0, 300, 180);
    for (int y = 48; y < 92; y++) {
      for (int x = 58; x < 202; x++) {
        assertEquals(BLUE.getRGB(), restored.getRGB(x, y), "Restored text must not become a white band");
      }
    }
    assertEquals(Color.BLACK.getRGB(), source.getRGB(80, 60), "Keep the input image intact");
  }

  @Test
  void rasterArtworkKeepsItsColorsAndRealWhiteDetailsAfterOcrTextExtraction() throws Exception {
    var source = filled(300, 180, BLUE);
    var graphics = source.createGraphics();
    graphics.setColor(ORANGE);
    graphics.fillRect(150, 0, 150, 180);
    graphics.setColor(Color.WHITE);
    graphics.fillRect(0, 140, 300, 2);
    graphics.setColor(Color.BLACK);
    graphics.fillRect(40, 40, 100, 50);
    graphics.dispose();

    var words = List.of(new OcrEngine.Word("Hello", 40, 40, 100, 50, 95, "one"));
    var draft = new PageLayoutAnalyzer().analyze(source, words);
    var artwork = draft.elements().stream().filter(item -> item.type().equals("image"))
        .findFirst().orElseThrow();
    String dataUrl = artwork.properties().get("src").toString();
    byte[] png = Base64.getDecoder().decode(dataUrl.substring(dataUrl.indexOf(',') + 1));
    var crop = ImageIO.read(new ByteArrayInputStream(png));
    int left = (int) artwork.x();
    int top = (int) artwork.y();

    assertEquals(BLUE.getRGB(), crop.getRGB(80 - left, 60 - top));
    assertEquals(ORANGE.getRGB(), crop.getRGB(220 - left, 60 - top));
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

    var draft = new PageLayoutAnalyzer().analyze(source, List.of());
    assertFalse(draft.elements().stream().anyMatch(item -> item.type().equals("line")),
        "A solid block boundary must stay in the artwork");
    assertFalse(draft.elements().isEmpty());
  }

  @Test
  void actualDarkRuleOnAColoredPanelStillBecomesAnEditableLine() {
    var source = filled(300, 180, BLUE);
    var graphics = source.createGraphics();
    graphics.setColor(Color.BLACK);
    graphics.drawLine(30, 90, 270, 90);
    graphics.dispose();

    var draft = new PageLayoutAnalyzer().analyze(source, List.of());
    assertEquals(1, draft.elements().stream().filter(item -> item.type().equals("line")).count());
  }

  @Test
  void aFullyMaskedCropWithoutBackgroundSamplesKeepsTheSourceInsteadOfTurningWhite() {
    var source = filled(60, 40, BLUE);
    boolean[] excluded = new boolean[60 * 40];
    Arrays.fill(excluded, true);
    var restored = ArtworkBackground.restore(source, excluded, 0, 0, 60, 40);
    assertEquals(BLUE.getRGB(), restored.getRGB(30, 20));
  }

  private static BufferedImage filled(int width, int height, Color color) {
    var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    var graphics = image.createGraphics();
    graphics.setColor(color);
    graphics.fillRect(0, 0, width, height);
    graphics.dispose();
    return image;
  }

  private static boolean[] mask(int width, int height, int left, int top, int w, int h) {
    boolean[] mask = new boolean[width * height];
    for (int y = top; y < top + h; y++) {
      Arrays.fill(mask, y * width + left, y * width + left + w, true);
    }
    return mask;
  }
}
