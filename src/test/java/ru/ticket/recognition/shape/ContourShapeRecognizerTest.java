package ru.ticket.recognition.shape;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class ContourShapeRecognizerTest {
  @Test
  void repeatedLargeBackgroundsUseBoundedPrimitiveStorage() {
    for (int scan = 0; scan < 3; scan++) {
      var image = new BufferedImage(2400, 2400, BufferedImage.TYPE_INT_RGB);
      assertThat(new ContourShapeRecognizer().recognize(image, new boolean[2400 * 2400])).isEmpty();
      image.flush();
    }
  }

  private final ContourShapeRecognizer recognizer = new ContourShapeRecognizer();

  @Test
  void texturedPhotoIsPreservedInsteadOfInventingAFlatRectangle() {
    BufferedImage img = blank(240, 240);
    for (int y = 40; y < 130; y++) {
      for (int x = 30; x < 130; x++) {
        img.setRGB(x, y, new Color(20 + x, 40 + y, (x * 3 + y) % 200).getRGB());
      }
    }
    var shapes = recognizer.recognize(img, new boolean[240 * 240]);
    assertThat(shapes).hasSize(1);
    assertThat(shapes.get(0).type()).isEqualTo("image");
    assertThat(shapes.get(0).properties().get("src").toString())
        .startsWith("data:image/png;base64,");
  }

  @Test
  void IrregularContourDoesNotBecomeAnInventedPolygonOrCircle() {
    BufferedImage img = blank(240, 240);
    Graphics2D g = img.createGraphics();
    try {
      g.setColor(Color.BLUE);
      g.fillRect(30, 30, 100, 25);
      g.fillRect(30, 55, 25, 75);
    } finally {
      g.dispose();
    }
    assertThat(recognizer.recognize(img, new boolean[240 * 240]))
        .singleElement()
        .extracting(item -> item.type())
        .isEqualTo("image");
  }

  @Test
  void ellipsePreservesItsAspectRatioAsPixels() {
    BufferedImage img = blank(240, 240);
    Graphics2D g = img.createGraphics();
    try {
      g.setColor(Color.BLUE);
      g.fillOval(30, 30, 140, 70);
    } finally {
      g.dispose();
    }
    assertThat(recognizer.recognize(img, new boolean[240 * 240]))
        .singleElement()
        .extracting(item -> item.type())
        .isEqualTo("image");
  }

  @Test
  void detectsRectangle() {
    BufferedImage img = blank(200, 150);
    Graphics2D g = img.createGraphics();
    try {
      g.setColor(Color.BLACK);
      g.fillRect(20, 20, 120, 80);
    } finally {
      g.dispose();
    }
    var shapes = recognizer.recognize(img, new boolean[200 * 150]);
    assertThat(shapes)
        .anyMatch(s -> "shape".equals(s.type()) && "rect".equals(s.properties().get("shape")));
  }

  @Test
  void detectsCircle() {
    BufferedImage img = blank(200, 200);
    Graphics2D g = img.createGraphics();
    try {
      g.setColor(Color.BLUE);
      g.fillOval(40, 40, 120, 120);
    } finally {
      g.dispose();
    }
    var shapes = recognizer.recognize(img, new boolean[200 * 200]);
    assertThat(shapes)
        .anyMatch(s -> "shape".equals(s.type()) && "circle".equals(s.properties().get("shape")));
  }

  @Test
  void textMaskExcludesGlyphsFromShapeDetection() {
    BufferedImage img = blank(200, 100);
    Graphics2D g = img.createGraphics();
    try {
      g.setColor(Color.BLACK);
      g.fillRect(20, 20, 100, 60);
      g.drawString("TEXT", 150, 50);
    } finally {
      g.dispose();
    }
    boolean[] mask = new boolean[200 * 100];
    for (int y = 30; y < 70; y++) {
      for (int x = 140; x < 195; x++) {
        mask[y * 200 + x] = true;
      }
    }
    var shapes = recognizer.recognize(img, mask);
    assertThat(shapes).isNotEmpty();
    assertThat(shapes).noneMatch(s -> s.x() > 140);
  }

  @Test
  void tinyNoiseIsIgnored() {
    BufferedImage img = blank(100, 100);
    Graphics2D g = img.createGraphics();
    try {
      g.setColor(Color.BLACK);
      g.fillRect(50, 50, 3, 3);
    } finally {
      g.dispose();
    }
    assertThat(recognizer.recognize(img, new boolean[100 * 100])).isEmpty();
  }

  private static BufferedImage blank(int w, int h) {
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    try {
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, w, h);
    } finally {
      g.dispose();
    }
    return img;
  }
}
