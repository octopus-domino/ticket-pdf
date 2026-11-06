package ru.ticket.recognition.layout;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import ru.ticket.recognition.OcrEngine.Word;
import ru.ticket.recognition.RecognitionDraft.Item;
import ru.ticket.recognition.RuleDetector.Rule;

/**
 * Детектор регулярной таблицы по полной сетке линий.
 *
 * <p><b>SRP.</b> Одна задача: если есть ортогональная сетка (≥3
 * горизонтальных «через всю ширину» и ≥2 вертикальных «через высоту»),
 * сгруппировать слова по ячейкам и вернуть один {@code Item("table")}.
 */
@Component
public class RuledTableDetector {

    private static final int MAX_HORIZONTAL_RULES = 31;
    private static final int MAX_VERTICAL_RULES = 16;
    private static final int SNAP_TOLERANCE = 5;
    private static final int EDGE_TOLERANCE = 7;

    public record Result(
            Optional<Item> table,
            Set<Word> consumedWords,
            List<Rule> remainingHorizontal,
            List<Rule> remainingVertical) {
    }

    public Result detect(
            BufferedImage image, List<Word> words,
            List<Rule> horizontal, List<Rule> vertical) {

        int width = image.getWidth();

        List<Rule> gridH = horizontal.stream().filter(r -> r.width() > width / 4).toList();
        List<Rule> gridV = vertical.stream().filter(r -> r.height() > 30).toList();

        if (gridH.size() < 3 || gridH.size() > MAX_HORIZONTAL_RULES
                || gridV.size() < 2 || gridV.size() > MAX_VERTICAL_RULES) {
            return empty(horizontal, vertical);
        }

        int left = gridH.get(0).x();
        int right = left + gridH.get(0).width();
        int top = gridH.get(0).y();
        int bottom = gridH.get(gridH.size() - 1).y();

        var xs = gridV.stream()
                .filter(r -> Math.abs(r.y() - top) <= SNAP_TOLERANCE
                        && Math.abs(r.y() + r.height() - bottom) <= EDGE_TOLERANCE)
                .map(Rule::x).distinct().sorted().toList();

        var ys = gridH.stream()
                .filter(r -> Math.abs(r.x() - left) <= SNAP_TOLERANCE
                        && Math.abs(r.x() + r.width() - right) <= SNAP_TOLERANCE)
                .map(Rule::y).distinct().sorted().toList();

        if (xs.size() < 2 || ys.size() < 3
                || Math.abs(xs.get(0) - left) > SNAP_TOLERANCE
                || Math.abs(xs.get(xs.size() - 1) - right) > EDGE_TOLERANCE) {
            return empty(horizontal, vertical);
        }

        var data = new ArrayList<List<String>>();
        var consumed = new HashSet<Word>();
        for (int row = 0; row < ys.size() - 1; row++) {
            var cells = new ArrayList<String>();
            for (int col = 0; col < xs.size() - 1; col++) {
                var inCell = new ArrayList<Word>();
                for (Word word : words) {
                    double cx = word.x() + word.width() / 2.0;
                    double cy = word.y() + word.height() / 2.0;
                    if (cx > xs.get(col) && cx < xs.get(col + 1)
                            && cy > ys.get(row) && cy < ys.get(row + 1)) {
                        inCell.add(word);
                    }
                }
                inCell.sort(Comparator.comparingInt(Word::y).thenComparingInt(Word::x));
                cells.add(String.join(" ", inCell.stream().map(Word::text).toList()));
                consumed.addAll(inCell);
            }
            data.add(cells);
        }

        Item table = new Item("table", left, top, right - left, bottom - top,
                Map.of(
                        "data", data,
                        "rows", data.size(),
                        "cols", xs.size() - 1,
                        "fontSize", Math.max(8, (ys.get(1) - top) * 0.35)));

        List<Rule> leftoverH = horizontal.stream()
                .filter(r -> r.y() < top - 3 || r.y() > bottom + 3).toList();
        List<Rule> leftoverV = vertical.stream()
                .filter(r -> r.x() < left - 3 || r.x() > right + 3).toList();

        return new Result(Optional.of(table), consumed, leftoverH, leftoverV);
    }

    private static Result empty(List<Rule> h, List<Rule> v) {
        return new Result(Optional.empty(), Set.of(), h, v);
    }
}