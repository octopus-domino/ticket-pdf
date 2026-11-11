package ru.ticket.recognition.tesseract;

import java.awt.image.BufferedImage;

/** OCR-only image preparation; source colors and word coordinates remain untouched. */
public final class TextPolarityNormalizer {
  private TextPolarityNormalizer() {}

  public static BufferedImage prepare(BufferedImage source) {
    int width = source.getWidth(), height = source.getHeight();
    int dark = 0, sampled = 0;
    for (int y = 0; y < height; y += 8)
      for (int x = 0; x < width; x += 8) {
        if (luminance(source.getRGB(x, y)) < 96) dark++;
        sampled++;
      }
    if (dark < sampled * .08) return source;
    int stride = width + 1;
    // Integer integral sums stay bounded for the recognition pixel limit.
    long[] integral = new long[Math.multiplyExact(stride, height + 1)];
    for (int y = 1; y <= height; y++) {
      long row = 0;
      for (int x = 1; x <= width; x++) {
        row += luminance(source.getRGB(x - 1, y - 1));
        integral[y * stride + x] = integral[(y - 1) * stride + x] + row;
      }
    }
    var result = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
    int radius = 20;
    for (int y = 0; y < height; y++)
      for (int x = 0; x < width; x++) {
        int left = Math.max(0, x - radius), right = Math.min(width, x + radius + 1);
        int top = Math.max(0, y - radius), bottom = Math.min(height, y + radius + 1);
        long sum =
            integral[bottom * stride + right]
                - integral[top * stride + right]
                - integral[bottom * stride + left]
                + integral[top * stride + left];
        int value = luminance(source.getRGB(x, y));
        if (sum < (long) (right - left) * (bottom - top) * 128) value = 255 - value;
        result.getRaster().setSample(x, y, 0, value);
      }
    return result;
  }

  private static int luminance(int rgb) {
    return ((rgb >> 16 & 255) * 77 + (rgb >> 8 & 255) * 150 + (rgb & 255) * 29) >> 8;
  }
}
