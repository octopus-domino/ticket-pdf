package ru.ticket.recognition;

import static org.assertj.core.api.Assertions.assertThat;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import ru.ticket.recognition.layout.*;
import ru.ticket.recognition.style.StableLineRunSplitter;

class StableRecognitionLayersTest {
  @Test void uncertainScannedWordsShareOneFontWhileNativePdfStylesRemainAuthoritative() {
    var image = new BufferedImage(240, 100, BufferedImage.TYPE_INT_RGB);
    var splitter = new StableLineRunSplitter();
    var words = List.of(new OcrEngine.Word("One", 0, 0, 30, 14, 90, "L1"),
        new OcrEngine.Word("phrase", 40, 0, 80, 18, 90, "L1"));
    var runs = splitter.split(image, words);
    assertThat(runs).hasSize(1);
    assertThat(runs.get(0).style().fontFamily()).isEqualTo("Arial");
    var hints = new OcrEngine.StyleHints("Times New Roman", 22.0, true, false);
    var nativeRuns = splitter.split(image, List.of(new OcrEngine.Word("Source", 0, 0, 80, 20, 100, "pdf:1", hints)));
    assertThat(nativeRuns.get(0).style().fontSize()).isEqualTo(22);
    assertThat(nativeRuns.get(0).style().fontFamily()).isEqualTo("Times New Roman");
    assertThat(nativeRuns.get(0).style().bold()).isTrue();
  }

  @Test void rasterArtworkRemovesOriginalTextAndRestoresOnlyTheVerifiedUniformBackground() throws Exception {
    var image = new BufferedImage(160, 160, BufferedImage.TYPE_INT_RGB);
    var graphics = image.createGraphics();
    graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 160, 160);
    graphics.setColor(Color.BLUE); graphics.fillRect(20, 20, 120, 120);
    graphics.setColor(Color.BLACK); graphics.fillRect(50, 50, 40, 40); graphics.dispose();
    boolean[] excluded = new boolean[160 * 160];
    TextMask.paint(excluded, 160, 160, 50, 50, 40, 40);
    var items = new ArtworkDetector().detect(image, excluded);
    var crop = items.stream().filter(i -> i.type().equals("image")).findFirst().orElseThrow();
    String data = (String) crop.properties().get("src");
    var decoded = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(data.split(",")[1])));
    assertThat(decoded.getRGB(65 - (int) crop.x(), 65 - (int) crop.y())).isEqualTo(Color.BLUE.getRGB());
    image.flush(); decoded.flush();
  }

  @Test void complexArtworkIsNotInventedInsideAnEditableTextRegion() throws Exception {
    var image = new BufferedImage(160, 160, BufferedImage.TYPE_INT_RGB);
    var graphics = image.createGraphics();
    graphics.setColor(Color.BLUE); graphics.fillRect(0, 0, 80, 160);
    graphics.setColor(Color.RED); graphics.fillRect(80, 0, 80, 160);
    graphics.setColor(Color.BLACK); graphics.fillRect(50, 50, 60, 40); graphics.dispose();
    boolean[] excluded = new boolean[160 * 160];
    TextMask.paint(excluded, 160, 160, 50, 50, 60, 40);
    var crop = new ArtworkDetector().detect(image, excluded).stream().filter(i -> i.type().equals("image")).findFirst().orElseThrow();
    String data = (String) crop.properties().get("src");
    var decoded = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(data.split(",")[1])));
    assertThat(decoded.getRGB(65 - (int) crop.x(), 65 - (int) crop.y()) >>> 24).isZero();
    image.flush(); decoded.flush();
  }
}
