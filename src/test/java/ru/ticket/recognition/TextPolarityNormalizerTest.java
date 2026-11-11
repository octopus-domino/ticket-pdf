package ru.ticket.recognition;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import ru.ticket.recognition.tesseract.TextPolarityNormalizer;

class TextPolarityNormalizerTest {
  @Test
  void mixedLightAndDarkPanelsKeepCoordinatesButUseDarkInkForOcr() {
    var source = new BufferedImage(300, 150, BufferedImage.TYPE_INT_RGB);
    var graphics = source.createGraphics();
    graphics.setColor(Color.WHITE);
    graphics.fillRect(0, 0, 300, 150);
    graphics.setColor(new Color(20, 30, 40));
    graphics.fillRect(150, 0, 150, 150);
    graphics.setColor(Color.BLACK);
    graphics.fillRect(50, 50, 4, 20);
    graphics.setColor(Color.WHITE);
    graphics.fillRect(200, 50, 4, 20);
    graphics.dispose();
    var prepared = TextPolarityNormalizer.prepare(source);
    assertThat(prepared.getWidth()).isEqualTo(300);
    assertThat(prepared.getHeight()).isEqualTo(150);
    assertThat(prepared.getRaster().getSample(51, 55, 0)).isLessThan(10);
    assertThat(prepared.getRaster().getSample(201, 55, 0)).isLessThan(10);
    assertThat(prepared.getRaster().getSample(180, 55, 0)).isGreaterThan(200);
    assertThat(source.getRGB(201, 55) & 0xffffff).isEqualTo(0xffffff);
  }

  @Test
  void normalPaperReusesSourceWithoutAnExtraPageAllocation() {
    var source = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
    var graphics = source.createGraphics();
    graphics.setColor(Color.WHITE);
    graphics.fillRect(0, 0, 100, 100);
    graphics.dispose();
    assertThat(TextPolarityNormalizer.prepare(source)).isSameAs(source);
  }
}
