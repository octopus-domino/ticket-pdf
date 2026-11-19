package ru.ticket.recognition.layout;

import java.awt.image.BufferedImage;
import java.util.*;
import org.springframework.stereotype.Component;
import ru.ticket.recognition.OcrEngine.Word;
import ru.ticket.recognition.RecognitionDraft.Item;
import ru.ticket.recognition.RuleDetector.Rule;
import ru.ticket.recognition.InkStyleSampler;

/** Connected-component grid analysis keeps separate tables separate, including short two-row forms. */
@Component
public class RuledTableDetector {
  private static final int SNAP = 6;
  private static final int MAX_ROWS = 80;
  private static final int MAX_COLS = 40;

  public record Result(List<Item> tables, Set<Word> consumedWords,
                       List<Rule> remainingHorizontal, List<Rule> remainingVertical) {
    /** Compatibility for callers interested in just the first table. */
    public Optional<Item> table() { return tables.stream().findFirst(); }
  }

  /** Words belong to one cell by their centre, so touching a border never duplicates a value. */
  public Result detect(BufferedImage image, List<Word> words, List<Rule> horizontal, List<Rule> vertical) {
    var tables = new ArrayList<Item>();
    var consumed = new HashSet<Word>();
    var usedH = new HashSet<Rule>();
    var usedV = new HashSet<Rule>();
    for (var group : components(horizontal, vertical)) {
      var hs = group.stream().filter(horizontal::contains).toList();
      var vs = group.stream().filter(vertical::contains).toList();
      Item table = build(image, words, hs, vs, consumed);
      if (table == null) continue;
      tables.add(table); usedH.addAll(hs); usedV.addAll(vs);
    }
    return new Result(List.copyOf(tables), Set.copyOf(consumed),
        horizontal.stream().filter(r -> !usedH.contains(r)).toList(),
        vertical.stream().filter(r -> !usedV.contains(r)).toList());
  }

  private List<List<Rule>> components(List<Rule> horizontal, List<Rule> vertical) {
    var visited = new HashSet<Rule>();
    var groups = new ArrayList<List<Rule>>();
    for (var seed : horizontal) {
      if (!visited.add(seed)) continue;
      var group = new ArrayList<Rule>();
      var queue = new ArrayDeque<Rule>(); queue.add(seed);
      while (!queue.isEmpty()) {
        var current = queue.remove(); group.add(current);
        var opposite = horizontal.contains(current) ? vertical : horizontal;
        for (var next : opposite)
          if (intersects(current, next) && visited.add(next)) queue.add(next);
      }
      groups.add(group);
    }
    return groups;
  }

  private boolean intersects(Rule a, Rule b) {
    return a.x() <= b.x() + b.width() + SNAP && b.x() <= a.x() + a.width() + SNAP
        && a.y() <= b.y() + b.height() + SNAP && b.y() <= a.y() + a.height() + SNAP;
  }

  private Item build(BufferedImage image, List<Word> words, List<Rule> hs, List<Rule> vs, Set<Word> consumed) {
    if (hs.size() < 2 || vs.size() < 2) return null;
    var xs = snap(vs.stream().map(Rule::x).toList());
    var ys = snap(hs.stream().map(Rule::y).toList());
    if (xs.size() < 2 || ys.size() < 2 || xs.size() > MAX_COLS + 1 || ys.size() > MAX_ROWS + 1) return null;
    int left = xs.get(0), right = xs.get(xs.size() - 1);
    int top = ys.get(0), bottom = ys.get(ys.size() - 1);
    if (right - left < 40 || bottom - top < 15 || !closed(hs, vs, left, top, right, bottom)) return null;
    var scannedHeights = words.stream().filter(w -> w.x() >= left && w.x() < right && w.y() >= top && w.y() < bottom)
        .mapToDouble(w -> w.height() * 1.3).sorted().toArray();
    double scannedSize = scannedHeights.length > 0 ? scannedHeights[scannedHeights.length / 2] : 12;
    var cells = cells(image, words, xs, ys, consumed, scannedSize);
    var data = cells.data();
    var sizes = words.stream().filter(consumed::contains).filter(w -> w.x() >= left && w.x() < right
        && w.y() >= top && w.y() < bottom).mapToInt(Word::height).sorted().toArray();
    double fontSize = sizes.length == 0 ? 12 : Math.max(8, sizes[sizes.length / 2] * 1.05);
    return new Item("table", left, top, right - left, bottom - top,
        Map.of("data", data, "rows", data.size(), "cols", xs.size() - 1, "fontSize", fontSize,
            "columnWidths", differences(xs), "rowHeights", differences(ys),
            "borderColor", borderColor(image, hs, vs), "headerBg", "#ffffff", "cellPadding", 2, "cellStyles", cells.styles()));
  }

  private String borderColor(BufferedImage image, List<Rule> horizontal, List<Rule> vertical) {
    var colors = new ArrayList<Integer>();
    for (var rule : java.util.stream.Stream.concat(horizontal.stream(), vertical.stream()).toList()) {
      boolean upright = rule.height() > rule.width();
      for (int part = 1; part <= 3; part++) {
        int x = rule.x() + (upright ? rule.width() / 2 : rule.width() * part / 4);
        int y = rule.y() + (upright ? rule.height() * part / 4 : rule.height() / 2);
        int color = image.getRGB(Math.min(image.getWidth() - 1, x), Math.min(image.getHeight() - 1, y));
        if ((color >> 16 & 255) + (color >> 8 & 255) + (color & 255) < 735) colors.add(color);
      }
    }
    if (colors.isEmpty()) return "#808080";
    int red = colors.stream().mapToInt(c -> c >> 16 & 255).sorted().toArray()[colors.size() / 2];
    int green = colors.stream().mapToInt(c -> c >> 8 & 255).sorted().toArray()[colors.size() / 2];
    int blue = colors.stream().mapToInt(c -> c & 255).sorted().toArray()[colors.size() / 2];
    return String.format(Locale.ROOT, "#%02x%02x%02x", red, green, blue);
  }

  private boolean closed(List<Rule> hs, List<Rule> vs, int left, int top, int right, int bottom) {
    return covers(hs, top, left, right) && covers(hs, bottom, left, right)
        && vs.stream().filter(r -> Math.abs(r.x() - left) <= SNAP || Math.abs(r.x() - right) <= SNAP)
        .filter(r -> r.y() <= top + SNAP * 2 && r.y() + r.height() >= bottom - SNAP * 2).count() >= 2;
  }

  /** A tinted header can split one border into adjacent spans with slightly different baselines. */
  private boolean covers(List<Rule> rules, int y, int left, int right) {
    int covered = left;
    for (var rule : rules.stream().filter(r -> Math.abs(r.y() - y) <= SNAP)
        .sorted(Comparator.comparingInt(Rule::x)).toList()) {
      if (rule.x() > covered + SNAP) break;
      covered = Math.max(covered, rule.x() + rule.width());
    }
    return covered >= right - SNAP;
  }

  private record Cells(List<List<String>> data, List<List<Map<String, Object>>> styles) {}

  private Cells cells(BufferedImage image, List<Word> words, List<Integer> xs, List<Integer> ys, Set<Word> consumed, double scannedSize) {
    var data = new ArrayList<List<String>>();
    var styles = new ArrayList<List<Map<String, Object>>>();
    for (int row = 0; row < ys.size() - 1; row++) {
      var values = new ArrayList<String>();
      var rowStyles = new ArrayList<Map<String, Object>>();
      for (int col = 0; col < xs.size() - 1; col++) {
        int left = xs.get(col), right = xs.get(col + 1), top = ys.get(row), bottom = ys.get(row + 1);
        var selected = words.stream().filter(w -> w.x() + w.width() / 2.0 >= left
            && w.x() + w.width() / 2.0 < right && w.y() + w.height() / 2.0 >= top
            && w.y() + w.height() / 2.0 < bottom)
            .sorted(Comparator.comparingInt(Word::y).thenComparingInt(Word::x)).toList();
        values.add(cellText(selected));
        rowStyles.add(cellStyle(image, selected, left, top, right, bottom, scannedSize));
        consumed.addAll(selected);
      }
      data.add(values);
      styles.add(rowStyles);
    }
    return new Cells(data, styles);
  }

  private String cellText(List<Word> words) {
    var lines = new LinkedHashMap<String, List<Word>>();
    for (var word : words) lines.computeIfAbsent(word.line(), key -> new ArrayList<>()).add(word);
    return lines.values().stream().sorted(Comparator.comparingInt(line -> line.stream().mapToInt(Word::y).min().orElse(0)))
        .map(line -> String.join(" ", line.stream().sorted(Comparator.comparingInt(Word::x)).map(Word::text).toList()))
        .collect(java.util.stream.Collectors.joining("\n"));
  }

  private Map<String, Object> cellStyle(BufferedImage image, List<Word> words, int left, int top, int right, int bottom, double scannedSize) {
    var fontSizes = words.stream().mapToDouble(w -> w.line() != null && w.line().startsWith("pdf:")
        ? w.hints().fontSize() : w.height() * 1.3).sorted().toArray();
    boolean nativeText = words.stream().anyMatch(w -> w.line() != null && w.line().startsWith("pdf:"));
    double size = nativeText && fontSizes.length > 0 ? fontSizes[fontSizes.length / 2] : scannedSize;
    String family = words.stream().filter(w -> w.line() != null && w.line().startsWith("pdf:"))
        .map(Word::hints).filter(Objects::nonNull)
        .map(h -> h.fontFamily()).filter(Objects::nonNull).findFirst().orElse("Arial");
    boolean bold = words.stream().filter(w -> w.line() != null && w.line().startsWith("pdf:") && w.hints() != null && Boolean.TRUE.equals(w.hints().bold())).count() > words.size() / 2;
    boolean italic = words.stream().filter(w -> w.line() != null && w.line().startsWith("pdf:") && w.hints() != null && Boolean.TRUE.equals(w.hints().italic())).count() > words.size() / 2;
    return Map.of("fontSize", Math.max(8, size), "fontFamily", family,
        "fontStyle", bold ? (italic ? "bold italic" : "bold") : italic ? "italic" : "normal",
        "color", InkStyleSampler.textColor(image, words), "background", background(image, left, top, right, bottom));
  }

  private String background(BufferedImage image, int left, int top, int right, int bottom) {
    var colors = new HashMap<Integer, Integer>();
    for (int y = top + 3; y < Math.min(image.getHeight(), bottom - 3); y += 3)
      for (int x = left + 3; x < Math.min(image.getWidth(), right - 3); x += 3)
        colors.merge(image.getRGB(x, y) & 0xffffff, 1, Integer::sum);
    int color = colors.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(0xffffff);
    return String.format(Locale.ROOT, "#%06x", color);
  }

  private List<Integer> snap(List<Integer> coordinates) {
    var result = new ArrayList<Integer>();
    for (int value : coordinates.stream().distinct().sorted().toList())
      if (result.isEmpty() || value - result.get(result.size() - 1) > SNAP) result.add(value);
    return result;
  }

  private List<Integer> differences(List<Integer> values) {
    var sizes = new ArrayList<Integer>();
    for (int i = 1; i < values.size(); i++) sizes.add(values.get(i) - values.get(i - 1));
    return sizes;
  }
}
