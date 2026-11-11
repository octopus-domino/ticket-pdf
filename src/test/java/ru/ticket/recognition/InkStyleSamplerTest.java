package ru.ticket.recognition;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;

class InkStyleSamplerTest {
  @Test
  void compressionAndAntialiasingDoNotReplaceInkWithGreyFringes() {
    for (boolean inverted : new boolean[] {false, true}) {
      var image = new BufferedImage(120, 40, BufferedImage.TYPE_INT_RGB);
      int background = inverted ? 20 : 245;
      int core = inverted ? 235 : 25;
      int fringe = (background + core) / 2;
      for (int y = 0; y < 40; y++)
        for (int x = 0; x < 120; x++) {
          int value = background;
          if (y >= 10 && y < 30 && x >= 10 && x < 110) {
            value = x % 5 == 0 ? core + y % 3 : fringe;
          }
          image.setRGB(x, y, new Color(value, value, value).getRGB());
        }
      String result =
          InkStyleSampler.textColor(
              image, List.of(new OcrEngine.Word("text", 10, 10, 100, 20, 99, "L")));
      int value = Integer.parseInt(result.substring(1, 3), 16);
      assertThat(Math.abs(value - core)).isLessThan(4);
    }
  }
}
