package ru.ticket.recognition;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;
import ru.ticket.recognition.RecognitionDraft.Item;

/** Deterministic geometric reconstruction; uncertain artwork stays an editable raster crop. */
@Component
public class PageLayoutAnalyzer {
  private record Rule(int x, int y, int width, int height) {}
  public RecognitionDraft analyze(BufferedImage image, List<OcrEngine.Word> words) {
    int w = image.getWidth(), h = image.getHeight();
    var items = new ArrayList<Item>();
    var warnings = new ArrayList<String>();
    boolean[] excluded = new boolean[w * h];
    for (var word : words) mask(excluded, w, h, word.x() - 2, word.y() - 2, word.width() + 4, word.height() + 4);
    List<Rule> horizontal = rules(image, false), vertical = rules(image, true);
    // A regular ruled table requires matching full-width/full-height lines, not just nearby text.
    List<Rule> gridH = horizontal.stream().filter(r -> r.width > w / 4).toList();
    List<Rule> gridV = vertical.stream().filter(r -> r.height > 30).toList();
    Set<OcrEngine.Word> cellWords = new HashSet<>();
    if (gridH.size() >= 3 && gridH.size() <= 31 && gridV.size() >= 2 && gridV.size() <= 16) {
      int left = gridH.get(0).x, right = left + gridH.get(0).width;
      int top = gridH.get(0).y, bottom = gridH.get(gridH.size() - 1).y;
      var xs = gridV.stream().filter(r -> Math.abs(r.y - top) <= 5 && Math.abs(r.y + r.height - bottom) <= 7)
          .map(r -> r.x).distinct().sorted().toList();
      var ys = gridH.stream().filter(r -> Math.abs(r.x - left) <= 5 && Math.abs(r.x + r.width - right) <= 5)
          .map(r -> r.y).distinct().sorted().toList();
      if (xs.size() >= 2 && ys.size() >= 3 && Math.abs(xs.get(0) - left) <= 5 && Math.abs(xs.get(xs.size() - 1) - right) <= 7) {
        var data = new ArrayList<List<String>>();
        for (int row = 0; row < ys.size() - 1; row++) {
          var cells = new ArrayList<String>();
          for (int col = 0; col < xs.size() - 1; col++) {
            var cell = new ArrayList<OcrEngine.Word>();
            for (var word : words) {
              double cx = word.x() + word.width() / 2.0, cy = word.y() + word.height() / 2.0;
              if (cx > xs.get(col) && cx < xs.get(col + 1) && cy > ys.get(row) && cy < ys.get(row + 1)) cell.add(word);
            }
            cell.sort(Comparator.comparingInt(OcrEngine.Word::y).thenComparingInt(OcrEngine.Word::x));
            cells.add(String.join(" ", cell.stream().map(OcrEngine.Word::text).toList())); cellWords.addAll(cell);
          }
          data.add(cells);
        }
        items.add(new Item("table", left, top, right - left, bottom - top,
            Map.of("data", data, "rows", data.size(), "cols", xs.size() - 1, "fontSize", Math.max(8, (ys.get(1) - top) * .35))));
        mask(excluded, w, h, left - 2, top - 2, right - left + 4, bottom - top + 4);
        horizontal = horizontal.stream().filter(r -> r.y < top - 3 || r.y > bottom + 3).toList();
        vertical = vertical.stream().filter(r -> r.x < left - 3 || r.x > right + 3).toList();
      }
    }
    var lines = new LinkedHashMap<String, List<OcrEngine.Word>>();
    for (var word : words) if (!cellWords.contains(word)) lines.computeIfAbsent(word.line(), k -> new ArrayList<>()).add(word);
    for (var line : lines.values()) {
      line.sort(Comparator.comparingInt(OcrEngine.Word::x));
      int x = line.stream().mapToInt(OcrEngine.Word::x).min().orElse(0), y = line.stream().mapToInt(OcrEngine.Word::y).min().orElse(0);
      int right = line.stream().mapToInt(a -> a.x() + a.width()).max().orElse(w), bottom = line.stream().mapToInt(a -> a.y() + a.height()).max().orElse(h);
      items.add(new Item("text", x, Math.max(0, y - 2), Math.min(w - x, right - x + 6), Math.min(h - y, bottom - y + 6),
          Map.of("text", String.join(" ", line.stream().map(OcrEngine.Word::text).toList()), "fontSize", Math.max(8, (bottom - y) * 1.05),
              "fill", "#202020", "confidence", line.stream().mapToDouble(OcrEngine.Word::confidence).average().orElse(0))));
    }
    for (var rule : horizontal) {
      items.add(new Item("line", rule.x, rule.y, rule.width, Math.max(1, rule.height), Map.of("stroke", "#555555")));
      mask(excluded, w, h, rule.x - 2, rule.y - 2, rule.width + 4, rule.height + 4);
    }
    for (var rule : vertical) {
      items.add(new Item("line", rule.x, rule.y, Math.max(1, rule.width), rule.height, Map.of("stroke", "#555555")));
      mask(excluded, w, h, rule.x - 2, rule.y - 2, rule.width + 4, rule.height + 4);
    }
    addArtwork(image, excluded, items);
    if (words.isEmpty()) warnings.add("recognition.no_text");
    if (words.stream().anyMatch(a -> a.confidence() < 65)) warnings.add("recognition.low_confidence");
    warnings.add("recognition.review_required");
    if (items.size() > 300) throw ru.ticket.common.ApiException.badRequest("recognition.too_complex");
    return new RecognitionDraft(w, h, items, warnings, "ocr");
  }
  private static List<Rule> rules(BufferedImage image, boolean vertical) {
    int across = vertical ? image.getHeight() : image.getWidth(), rows = vertical ? image.getWidth() : image.getHeight();
    var result = new ArrayList<Rule>();
    for (int row = 0; row < rows; row++) {
      int start = -1, gap = 0;
      for (int pos = 0; pos <= across; pos++) {
        boolean dark = pos < across && dark(image.getRGB(vertical ? row : pos, vertical ? pos : row));
        if (dark) { if (start < 0) start = pos; gap = 0; }
        else if (start >= 0 && (++gap > 1 || pos == across)) {
          int length = pos - start - gap + 1;
          if (length >= Math.max(50, across / 12) && thin(image, vertical, row, start, length)) {
            Rule next = vertical ? new Rule(row, start, 1, length) : new Rule(start, row, length, 1);
            if (result.isEmpty() || !adjacent(result.get(result.size() - 1), next, vertical)) result.add(next);
          }
          start = -1; gap = 0;
        }
      }
    }
    return result.size() <= 80 ? result : List.of();
  }
  private static boolean thin(BufferedImage image, boolean vertical, int row, int start, int length) {
    int thick = 0;
    for (int quarter = 1; quarter <= 3; quarter++) {
      int pos = start + length * quarter / 4;
      int x = vertical ? row : pos, y = vertical ? pos : row;
      int ax = vertical ? x - 3 : x, ay = vertical ? y : y - 3;
      int bx = vertical ? x + 3 : x, by = vertical ? y : y + 3;
      if (ax >= 0 && ay >= 0 && bx < image.getWidth() && by < image.getHeight()
          && dark(image.getRGB(ax, ay)) && dark(image.getRGB(bx, by))) thick++;
    }
    return thick < 2;
  }
  private static boolean adjacent(Rule a, Rule b, boolean vertical) {
    return vertical ? Math.abs(a.x - b.x) <= 3 && Math.abs(a.y - b.y) <= 2 && Math.abs(a.height - b.height) <= 3
        : Math.abs(a.y - b.y) <= 3 && Math.abs(a.x - b.x) <= 2 && Math.abs(a.width - b.width) <= 3;
  }
  private static boolean dark(int rgb) { return ((rgb >> 16 & 255) + (rgb >> 8 & 255) + (rgb & 255)) < 360; }
  private static void mask(boolean[] mask, int w, int h, int x, int y, int width, int height) {
    for (int row = Math.max(0, y); row < Math.min(h, y + height); row++)
      Arrays.fill(mask, row * w + Math.max(0, x), row * w + Math.max(0, Math.min(w, x + width)), true);
  }
  private static void addArtwork(BufferedImage image, boolean[] excluded, List<Item> items) {
    int tile = 12, w = image.getWidth(), h = image.getHeight(), cols = (w + tile - 1) / tile, rows = (h + tile - 1) / tile;
    boolean[] ink = new boolean[cols * rows], seen = new boolean[cols * rows];
    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
      int rgb = image.getRGB(x, y);
      if (!excluded[y * w + x] && ((rgb >> 16 & 255) < 220 || (rgb >> 8 & 255) < 220 || (rgb & 255) < 220)) ink[(y / tile) * cols + x / tile] = true;
    }
    int crops = 0;
    for (int i = 0; i < ink.length; i++) {
      if (!ink[i] || seen[i]) continue;
      var queue = new ArrayDeque<Integer>(); queue.add(i); seen[i] = true;
      int x0 = cols, y0 = rows, x1 = 0, y1 = 0, count = 0;
      while (!queue.isEmpty()) {
        int cell = queue.removeFirst(), x = cell % cols, y = cell / cols; count++;
        x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y);
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
          int nx = x + dx, ny = y + dy;
          if (nx < 0 || nx >= cols || ny < 0 || ny >= rows) continue;
          int next = ny * cols + nx;
          if (!seen[next] && ink[next]) { seen[next] = true; queue.add(next); }
        }
      }
      int x = x0 * tile, y = y0 * tile, width = Math.min(w, (x1 + 1) * tile) - x, height = Math.min(h, (y1 + 1) * tile) - y;
      if (count < 5 || width < 24 || height < 24 || ++crops > 32) continue;
      // Excluded text must not be duplicated inside the crop.
      var crop = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      for (int cy = 0; cy < height; cy++) for (int cx = 0; cx < width; cx++)
        crop.setRGB(cx, cy, excluded[(y + cy) * w + x + cx] ? 0xffffff : image.getRGB(x + cx, y + cy));
      int[] colors = new int[4096];
      for (int cy = 0; cy < height; cy++) for (int cx = 0; cx < width; cx++) {
        int rgb = crop.getRGB(cx, cy);
        colors[(rgb >> 20 & 15) * 256 + (rgb >> 12 & 15) * 16 + (rgb >> 4 & 15)]++;
      }
      int dominant = 0;
      for (int c = 1; c < colors.length; c++) if (colors[c] > colors[dominant]) dominant = c;
      if (colors[dominant] > width * height * .92 && dominant != 4095) {
        String fill = String.format(Locale.ROOT, "#%02x%02x%02x", ((dominant >> 8) & 15) * 17, ((dominant >> 4) & 15) * 17, (dominant & 15) * 17);
        items.add(0, new Item("shape", x, y, width, height, Map.of("shape", "rect", "fill", fill)));
      } else items.add(0, new Item("image", x, y, width, height, Map.of("src", png(crop))));
    }
  }
  public static String png(BufferedImage image) {
    try { var output = new ByteArrayOutputStream(); ImageIO.write(image, "png", output); return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray()); }
    catch (java.io.IOException error) { throw new IllegalStateException("Image encoding failed", error); }
  }
}
