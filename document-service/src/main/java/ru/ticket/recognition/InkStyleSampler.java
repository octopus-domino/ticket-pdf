package ru.ticket.recognition;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class InkStyleSampler {

  private InkStyleSampler() {}

  public static String textColor(BufferedImage image, List<OcrEngine.Word> words) {
    var colors = new ColorHistogram();
    for (var word : words) {
      int left = Math.max(0, word.x());
      int top = Math.max(0, word.y());
      int right = Math.min(image.getWidth(), word.x() + word.width());
      int bottom = Math.min(image.getHeight(), word.y() + word.height());
      var border = new ColorHistogram();
      for (int y = Math.max(0, top - 3); y < Math.min(image.getHeight(), bottom + 3); y++) {
        for (int x = Math.max(0, left - 3); x < Math.min(image.getWidth(), right + 3); x++) {
          if (x < left || x >= right || y < top || y >= bottom) border.add(image.getRGB(x, y));
        }
      }
      int background = border.dominant(0xffffff);
      int maximumContrast = 0;
      for (int y = top; y < bottom; y++)
        for (int x = left; x < right; x++)
          maximumContrast = Math.max(maximumContrast, distance(image.getRGB(x, y), background));
      // Edge antialiasing and JPEG ringing are mixtures with the background, not ink color.
      double threshold = Math.max(3600, maximumContrast * 0.72);
      for (int y = top; y < bottom; y++)
        for (int x = left; x < right; x++) {
          int rgb = image.getRGB(x, y);
          if (distance(rgb, background) >= threshold) colors.add(rgb);
        }
    }
    return hex(colors.dominant(0x202020));
  }

  /** Fixed storage and quantized bins keep compression noise from splitting one ink color. */
  private static final class ColorHistogram {
    private final int[] counts = new int[4096];
    private final long[] red = new long[4096], green = new long[4096], blue = new long[4096];

    void add(int rgb) {
      int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
      int bin = (r >> 4) * 256 + (g >> 4) * 16 + (b >> 4);
      counts[bin]++;
      red[bin] += r;
      green[bin] += g;
      blue[bin] += b;
    }

    int dominant(int fallback) {
      int best = 0;
      for (int i = 1; i < counts.length; i++) if (counts[i] > counts[best]) best = i;
      int count = counts[best];
      return count == 0
          ? fallback
          : (int) (red[best] / count) << 16
              | (int) (green[best] / count) << 8
              | (int) (blue[best] / count);
    }
  }

  static String ruleColor(BufferedImage image, int x, int y, int width, int height) {
    var colors = new HashMap<Integer, Integer>();
    for (int row = y; row < y + height; row++) {
      for (int col = x; col < x + width; col++) {
        int rgb = image.getRGB(col, row) & 0xffffff;
        if (((rgb >> 16) & 255) + ((rgb >> 8) & 255) + (rgb & 255) < 360) {
          colors.merge(rgb, 1, Integer::sum);
        }
      }
    }
    return hex(dominant(colors, 0x555555));
  }

  private static int background(BufferedImage image, int left, int top, int right, int bottom) {
    var colors = new HashMap<Integer, Integer>();
    for (int y = Math.max(0, top - 2); y < Math.min(image.getHeight(), bottom + 2); y++) {
      for (int x = Math.max(0, left - 2); x < Math.min(image.getWidth(), right + 2); x++) {
        if (x < left || x >= right || y < top || y >= bottom) {
          colors.merge(image.getRGB(x, y) & 0xffffff, 1, Integer::sum);
        }
      }
    }
    return dominant(colors, 0xffffff);
  }

  private static int dominant(Map<Integer, Integer> colors, int fallback) {
    return colors.entrySet().stream()
        .max(Map.Entry.<Integer, Integer>comparingByValue().thenComparingInt(Map.Entry::getKey))
        .map(Map.Entry::getKey)
        .orElse(fallback);
  }

  private static int distance(int a, int b) {
    int r = (a >> 16 & 255) - (b >> 16 & 255);
    int g = (a >> 8 & 255) - (b >> 8 & 255);
    int blue = (a & 255) - (b & 255);
    return r * r + g * g + blue * blue;
  }

  private static String hex(int rgb) {
    return String.format(Locale.ROOT, "#%06x", rgb);
  }
}
