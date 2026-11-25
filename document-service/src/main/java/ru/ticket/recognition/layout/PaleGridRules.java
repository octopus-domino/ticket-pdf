package ru.ticket.recognition.layout;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import ru.ticket.recognition.RuleDetector.Rule;

/** Strategy for pale table rules on tinted cells; unused candidates never become artwork or lines. */
public final class PaleGridRules {
    private static final int NEIGHBOUR = 3;
    private static final int MIN_CONTRAST = 35;
    private static final int MIN_LENGTH = 80;
    private static final int MAX_GAP = 3;

    private PaleGridRules() {}

    public static List<Rule> detect(BufferedImage image, boolean vertical) {
        int across = vertical ? image.getHeight() : image.getWidth();
        int rows = vertical ? image.getWidth() : image.getHeight();
        var rules = new ArrayList<Rule>();
        for (int row = NEIGHBOUR + 1; row < rows - NEIGHBOUR - 1; row++) {
            int start = -1, gap = 0;
            for (int pos = 0; pos <= across; pos++) {
                if (pos < across && candidate(image, vertical, row, pos)) {
                    if (start < 0) start = pos;
                    gap = 0;
                } else if (start >= 0 && (++gap > MAX_GAP || pos == across)) {
                    int length = pos - gap - start + 1;
                    if (length >= MIN_LENGTH && uniform(image, vertical, row, start, length))
                        merge(rules, vertical ? new Rule(row, start, 1, length) : new Rule(start, row, length, 1), vertical);
                    start = -1; gap = 0;
                }
            }
        }
        return rules.size() <= 100 ? rules : List.of();
    }

    private static boolean candidate(BufferedImage image, boolean vertical, int row, int pos) {
        int x = vertical ? row : pos, y = vertical ? pos : row;
        int center = light(image.getRGB(x, y));
        if (center <= 500 || center >= 735) return false;
        int before = light(image.getRGB(vertical ? x - NEIGHBOUR : x, vertical ? y : y - NEIGHBOUR));
        int after = light(image.getRGB(vertical ? x + NEIGHBOUR : x, vertical ? y : y + NEIGHBOUR));
        return Math.max(before, after) - center >= MIN_CONTRAST;
    }

    private static boolean uniform(BufferedImage image, boolean vertical, int row, int start, int length) {
        int minimum = 765, maximum = 0, hits = 0;
        for (int sample = 1; sample <= 31; sample++) {
            int pos = start + length * sample / 32;
            int shade = light(image.getRGB(vertical ? row : pos, vertical ? pos : row));
            minimum = Math.min(minimum, shade); maximum = Math.max(maximum, shade);
            if (candidate(image, vertical, row, pos)) hits++;
        }
        return hits >= 24 && maximum - minimum <= 120;
    }

    private static void merge(List<Rule> rules, Rule next, boolean vertical) {
        for (int index = rules.size() - 1; index >= 0; index--) {
            var old = rules.get(index);
            boolean aligned = Math.abs(vertical ? old.x() - next.x() : old.y() - next.y()) <= 3;
            boolean overlap = vertical ? old.y() <= next.y() + next.height() + 3 && next.y() <= old.y() + old.height() + 3
                : old.x() <= next.x() + next.width() + 3 && next.x() <= old.x() + old.width() + 3;
            if (!aligned || !overlap) continue;
            int x = Math.min(old.x(), next.x()), y = Math.min(old.y(), next.y());
            rules.set(index, new Rule(x, y, Math.max(old.x() + old.width(), next.x() + next.width()) - x,
                Math.max(old.y() + old.height(), next.y() + next.height()) - y));
            return;
        }
        rules.add(next);
    }

    private static int light(int rgb) { return (rgb >> 16 & 255) + (rgb >> 8 & 255) + (rgb & 255); }
}
