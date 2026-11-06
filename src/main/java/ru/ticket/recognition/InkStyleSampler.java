package ru.ticket.recognition;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class InkStyleSampler {

  private InkStyleSampler() {
  }

  public static String textColor(BufferedImage image, List<OcrEngine.Word> words) {
    var colors = new HashMap<Integer, Integer>();
    for (var word : words) {
      int left = Math.max(0, word.x());
      int top = Math.max(0, word.y());
      int right = Math.min(image.getWidth(), word.x() + word.width());
      int bottom = Math.min(image.getHeight(), word.y() + word.height());

      // Sample the border for background
      var borderColors = new HashMap<Integer, Integer>();
      for (int y = Math.max(0, top - 5); y < Math.min(image.getHeight(), bottom + 5); y++) {
        for (int x = Math.max(0, left - 5); x < Math.min(image.getWidth(), right + 5); x++) {
          if (x < left || x >= right || y < top || y >= bottom) {
            borderColors.merge(image.getRGB(x, y) & 0xffffff, 1, Integer::sum);
          }
        }
      }
      int background = dominant(borderColors, 0xffffff);

      // Sample the inside for ink
      for (int y = top; y < bottom; y++) {
        for (int x = left; x < right; x++) {
          int rgb = image.getRGB(x, y) & 0xffffff;
          if (distance(rgb, background) > 3600) {
            colors.merge(rgb, 1, Integer::sum);
          }
        }
      }
    }
    return hex(dominant(colors, 0x202020));
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

  private static int background(
          BufferedImage image, int left, int top, int right, int bottom) {
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
            .max(Map.Entry.<Integer, Integer>comparingByValue()
                    .thenComparingInt(Map.Entry::getKey))
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