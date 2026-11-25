package ru.ticket.image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/** Removes only redundant opacity; never resizes artwork or quantizes its colours. */
public final class LosslessPng {
  private LosslessPng() {}

  public static boolean transparent(BufferedImage image) {
    if (!image.getColorModel().hasAlpha()) return false;
    for (int y = 0; y < image.getHeight(); y++)
      for (int x = 0; x < image.getWidth(); x++)
        if ((image.getRGB(x, y) >>> 24) != 255) return true;
    return false;
  }

  public static byte[] encode(BufferedImage source) throws IOException {
    boolean alpha = transparent(source);
    var image = new BufferedImage(source.getWidth(), source.getHeight(),
        alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
    // Copy samples exactly: Graphics2D may round partially transparent pixels.
    int[] row = new int[source.getWidth()];
    for (int y = 0; y < source.getHeight(); y++) {
      source.getRGB(0, y, source.getWidth(), 1, row, 0, row.length);
      image.setRGB(0, y, source.getWidth(), 1, row, 0, row.length);
    }
    var bytes = new ByteArrayOutputStream();
    var writer = ImageIO.getImageWritersByFormatName("png").next();
    try (var output = new MemoryCacheImageOutputStream(bytes)) {
      writer.setOutput(output);
      var params = writer.getDefaultWriteParam();
      if (params.canWriteCompressed()) {
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(0); // JDK PNG: strongest lossless compression.
      }
      writer.write(null, new javax.imageio.IIOImage(image, null, null), params);
    } finally {
      writer.dispose();
      image.flush();
    }
    return bytes.toByteArray();
  }
}
