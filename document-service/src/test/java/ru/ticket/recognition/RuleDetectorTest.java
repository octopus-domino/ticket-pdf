package ru.ticket.recognition;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class RuleDetectorTest {
  @Test
  void straightPhotographicEdgeOnTexturedBackgroundDoesNotBecomeAPrintedRule() {
    var image = new BufferedImage(300, 160, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < 160; y++)
      for (int x = 0; x < 300; x++) {
        int shade = 100 + (x * 17 + y * 13) % 150;
        image.setRGB(x, y, new Color(shade, shade, shade).getRGB());
      }
    for (int x = 20; x < 280; x++) image.setRGB(x, 80, Color.BLACK.getRGB());
    assertThat(RuleDetector.detect(image, false)).isEmpty();
  }

  @Test
  void realHorizontalAndVerticalPrintedRulesAreRetained() {
    var image = new BufferedImage(300, 160, BufferedImage.TYPE_INT_RGB);
    var graphics = image.createGraphics();
    graphics.setColor(Color.WHITE);
    graphics.fillRect(0, 0, 300, 160);
    graphics.setColor(Color.BLACK);
    graphics.fillRect(20, 20, 260, 1);
    graphics.fillRect(20, 40, 1, 100);
    graphics.dispose();
    assertThat(RuleDetector.detect(image, false)).hasSize(1);
    assertThat(RuleDetector.detect(image, true)).hasSize(1);
  }
}
