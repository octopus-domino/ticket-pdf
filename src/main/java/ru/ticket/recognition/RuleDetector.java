package ru.ticket.recognition;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Detects complete ruled strokes, merging scan rows before artwork masking. */
final class RuleDetector {
  private static final int RULE_NEIGHBOR_DISTANCE = 3;
  private static final int MIN_RULE_CONTRAST = 60;
  record Rule(int x, int y, int width, int height) {}
  private RuleDetector() {}

  static List<Rule> detect(BufferedImage image, boolean vertical) {
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
            Rule next = fullThickness(image, vertical, row, start, length);
            boolean merged = false;
            // A scan row can contain several separate rules. Comparing only the last rule
            // creates one editable duplicate per pixel row for all preceding segments.
            for (int index = result.size() - 1; index >= 0; index--) {
              Rule previous = result.get(index);
              if (!adjacent(previous, next, vertical)) continue;
              int x = Math.min(previous.x, next.x), y = Math.min(previous.y, next.y);
              result.set(index, new Rule(x, y,
                  Math.max(previous.x + previous.width, next.x + next.width) - x,
                  Math.max(previous.y + previous.height, next.y + next.height) - y));
              merged = true;
              break;
            }
            if (!merged) result.add(next);
          }
          start = -1; gap = 0;
        }
      }
    }
    return result.size() <= 80 ? result : List.of();
  }
  private static Rule fullThickness(BufferedImage image, boolean vertical, int row, int start, int length) {
    int limit = vertical ? image.getWidth() : image.getHeight();
    int first = row, last = row;
    while (first > 0 && row - first < 5 && inkSamples(image, vertical, first - 1, start, length) >= 2) first--;
    while (last + 1 < limit && last - first < 5 && inkSamples(image, vertical, last + 1, start, length) >= 2) last++;
    return vertical ? new Rule(first, start, last - first + 1, length) : new Rule(start, first, length, last - first + 1);
  }

  private static int inkSamples(BufferedImage image, boolean vertical, int row, int start, int length) {
    int count = 0;
    for (int quarter = 1; quarter <= 3; quarter++) {
      int pos = start + length * quarter / 4;
      if (dark(image.getRGB(vertical ? row : pos, vertical ? pos : row))) count++;
    }
    return count;
  }
  private static boolean thin(BufferedImage image, boolean vertical, int row, int start, int length) {
    int contrastingSamples = 0;
    for (int quarter = 1; quarter <= 3; quarter++) {
      int pos = start + length * quarter / 4;
      int x = vertical ? row : pos;
      int y = vertical ? pos : row;
      int beforeX = vertical ? x - RULE_NEIGHBOR_DISTANCE : x;
      int beforeY = vertical ? y : y - RULE_NEIGHBOR_DISTANCE;
      int afterX = vertical ? x + RULE_NEIGHBOR_DISTANCE : x;
      int afterY = vertical ? y : y + RULE_NEIGHBOR_DISTANCE;
      if (beforeX < 0 || beforeY < 0
          || afterX >= image.getWidth() || afterY >= image.getHeight()) {
        continue;
      }
      int center = brightness(image.getRGB(x, y));
      // A rule must contrast with BOTH sides. A solid panel edge has the panel's color
      // on one side and must remain part of the artwork rather than being erased as a rule.
      if (brightness(image.getRGB(beforeX, beforeY)) > center + MIN_RULE_CONTRAST
          && brightness(image.getRGB(afterX, afterY)) > center + MIN_RULE_CONTRAST) {
        contrastingSamples++;
      }
    }
    return contrastingSamples >= 2;
  }

  private static boolean adjacent(Rule a, Rule b, boolean vertical) {
    return vertical ? a.x < b.x + b.width && b.x < a.x + a.width && Math.abs(a.y - b.y) <= 2 && Math.abs(a.height - b.height) <= 3
        : a.y < b.y + b.height && b.y < a.y + a.height && Math.abs(a.x - b.x) <= 2 && Math.abs(a.width - b.width) <= 3;
  }
  private static int brightness(int rgb) {
    return (rgb >> 16 & 255) + (rgb >> 8 & 255) + (rgb & 255);
  }

  private static boolean dark(int rgb) {
    return brightness(rgb) < 360;
  }
}
