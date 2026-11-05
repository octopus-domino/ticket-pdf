package ru.ticket.recognition;

import java.awt.image.BufferedImage;

/** Rebuild the background beneath text and rules extracted as editable objects. */
final class ArtworkBackground {
  private static final int[] DX = {-1, 1, 0, 0};
  private static final int[] DY = {0, 0, -1, 1};

  private ArtworkBackground() {}

  static BufferedImage restore(
      BufferedImage source, boolean[] excluded, int left, int top, int width, int height) {
    var crop = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    boolean[] known = new boolean[width * height];
    boolean hasExcludedPixels = false;
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        crop.setRGB(x, y, source.getRGB(left + x, top + y));
        known[y * width + x] = !excluded[(top + y) * source.getWidth() + left + x];
        hasExcludedPixels |= !known[y * width + x];
      }
    }
    if (!hasExcludedPixels) return crop;

    // Seed only from original unmasked pixels. Reusing a newly filled pixel here would
    // bias the result toward the scan direction rather than the nearest visible background.
    int[] pending = new int[width * height];
    int tail = 0;
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int pixel = y * width + x;
        if (known[pixel]) continue;
        int red = 0;
        int green = 0;
        int blue = 0;
        int samples = 0;
        for (int direction = 0; direction < DX.length; direction++) {
          int nx = x + DX[direction];
          int ny = y + DY[direction];
          if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
          if (excluded[(top + ny) * source.getWidth() + left + nx]) continue;
          int rgb = source.getRGB(left + nx, top + ny);
          red += (rgb >> 16) & 255;
          green += (rgb >> 8) & 255;
          blue += rgb & 255;
          samples++;
        }
        if (samples == 0) continue;
        crop.setRGB(x, y, ((red / samples) << 16) | ((green / samples) << 8) | (blue / samples));
        known[pixel] = true;
        pending[tail++] = pixel;
      }
    }

    // A bounded multi-source flood fills each masked pixel once. It needs no model and
    // avoids inventing white stripes on colored paper. Unmasked artwork stays unchanged.
    int head = 0;
    while (head < tail) {
      int pixel = pending[head++];
      int x = pixel % width;
      int y = pixel / width;
      int rgb = crop.getRGB(x, y);
      for (int direction = 0; direction < DX.length; direction++) {
        int nx = x + DX[direction];
        int ny = y + DY[direction];
        if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
        int next = ny * width + nx;
        if (known[next]) continue;
        crop.setRGB(nx, ny, rgb);
        known[next] = true;
        pending[tail++] = next;
      }
    }
    // If a crop has no usable boundary, keep the source pixels instead of painting them white.
    return crop;
  }
}
