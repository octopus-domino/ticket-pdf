package ru.ticket.recognition.shape;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import ru.ticket.recognition.ImageEncoding;
import ru.ticket.recognition.RecognitionDraft.Item;

/**
 * Contour-based shape recognizer.
 *
 * <p><b>Large contours are ignored.</b> A contour that covers more than 40% of the page is a
 * background panel or a segmentation artifact (e.g. the diagonal of a card being approximated as a
 * giant black triangle). Such regions are handled by {@code ArtworkDetector} as raster crops.
 * Letting them through here produces an opaque primitive covering the whole page.
 */
@Component
public class ContourShapeRecognizer implements ShapeRecognizer {

  private static final int MIN_AREA = 400;
  private static final int MAX_SHAPES = 64;
  private static final double MAX_PAGE_AREA_RATIO = 0.4;

  @Override
  public List<Item> recognize(BufferedImage image, boolean[] textMask) {
    int w = image.getWidth(), h = image.getHeight();
    boolean[] ink = new boolean[w * h];
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        if (textMask[y * w + x]) continue;
        int rgb = image.getRGB(x, y);
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        ink[y * w + x] = r < 245 || g < 245 || b < 245;
      }
    }

    boolean[] visited = new boolean[w * h];
    int[] pending = new int[w * h];
    List<Item> result = new ArrayList<>();

    for (int i = 0; i < ink.length && result.size() < MAX_SHAPES; i++) {
      if (!ink[i] || visited[i]) continue;
      Component component = floodFill(image, ink, visited, pending, w, h, i);
      if (component.count() < MIN_AREA) continue;

      int minX = component.left(),
          minY = component.top(),
          maxX = component.right(),
          maxY = component.bottom();
      int bw = maxX - minX + 1, bh = maxY - minY + 1;
      if (bw < 20 || bh < 20) continue;

      // Гигантские контуры — это фон/артефакт, его обрабатывает ArtworkDetector.
      if ((long) bw * bh > (long) w * h * MAX_PAGE_AREA_RATIO) {
        continue;
      }

      double fillRatio = (double) component.count() / (bw * bh);
      boolean flatColor = component.purity() >= 0.92;
      boolean round = Math.abs((double) bw / bh - 1) < 0.04;
      if (flatColor
          && round
          && Math.abs(fillRatio - Math.PI / 4) < 0.04
          && matchesEllipse(pending, component.count(), w, minX, minY, bw, bh)) {
        result.add(primitive("circle", component, bw, bh));
      } else if (flatColor && fillRatio >= 0.985) {
        result.add(primitive("rect", component, bw, bh));
      } else {
        // Uncertain contours retain their pixels. A bounding box is not a polygon.
        result.add(
            new Item(
                "image",
                minX,
                minY,
                bw,
                bh,
                Map.of("src", ImageEncoding.pngDataUrl(image.getSubimage(minX, minY, bw, bh)))));
      }
    }
    return result;
  }

  private record Component(
      int count, int left, int top, int right, int bottom, double purity, String color) {}

  private Component floodFill(
      BufferedImage image,
      boolean[] ink,
      boolean[] visited,
      int[] pending,
      int w,
      int h,
      int start) {
    int head = 0, tail = 1, left = w, top = h, right = 0, bottom = 0;
    pending[0] = start;
    visited[start] = true;
    int[] colors = new int[4096];
    long[] red = new long[4096], green = new long[4096], blue = new long[4096];
    while (head < tail) {
      int pixel = pending[head++], x = pixel % w, y = pixel / w;
      left = Math.min(left, x);
      top = Math.min(top, y);
      right = Math.max(right, x);
      bottom = Math.max(bottom, y);
      int rgb = image.getRGB(x, y), r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
      int bin = (r >> 4) * 256 + (g >> 4) * 16 + (b >> 4);
      colors[bin]++;
      red[bin] += r;
      green[bin] += g;
      blue[bin] += b;
      for (int dy = -1; dy <= 1; dy++)
        for (int dx = -1; dx <= 1; dx++) {
          int nx = x + dx, ny = y + dy;
          if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
          int next = ny * w + nx;
          if (ink[next] && !visited[next]) {
            visited[next] = true;
            pending[tail++] = next;
          }
        }
    }
    int best = 0;
    for (int i = 1; i < colors.length; i++) if (colors[i] > colors[best]) best = i;
    int count = Math.max(1, colors[best]);
    String color =
        String.format(
            Locale.ROOT,
            "#%02x%02x%02x",
            red[best] / count,
            green[best] / count,
            blue[best] / count);
    return new Component(tail, left, top, right, bottom, (double) count / tail, color);
  }

  private Item primitive(String shape, Component component, int width, int height) {
    return new Item(
        "shape",
        component.left(),
        component.top(),
        width,
        height,
        Map.of(
            "shape",
            shape,
            "fill",
            component.color(),
            "stroke",
            "transparent",
            "strokeWidth",
            0.0));
  }

  private boolean matchesEllipse(
      int[] pixels, int count, int pageWidth, int left, int top, int width, int height) {
    int outside = 0;
    for (int i = 0; i < count; i++) {
      double x = 2.0 * (pixels[i] % pageWidth - left + 0.5) / width - 1;
      double y = 2.0 * (pixels[i] / pageWidth - top + 0.5) / height - 1;
      if (x * x + y * y > 1.04) outside++;
    }
    return outside <= count * 0.01;
  }
}
