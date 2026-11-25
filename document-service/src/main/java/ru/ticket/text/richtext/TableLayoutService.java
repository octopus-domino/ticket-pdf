package ru.ticket.text.richtext;

import ru.ticket.text.TextLayoutService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TableLayoutService {

    private static final double MIN_ROW_HEIGHT = 12;
    public static final double CELL_BLOCK_MARGIN = 8;
    private static final int MAX_DEPTH = 4;
    private static final double DEFAULT_LINE_HEIGHT_COEF = 1.5;

    private final TextLayoutService measure;

    public TableLayoutService(TextLayoutService measure) {
        this.measure = measure;
    }

    public Layout compute(RichTableData data, double width, int baseFontSize,
                          String fontFamily, double lineHeightCoef) {
        return compute(data, width, baseFontSize, fontFamily, lineHeightCoef, 0);
    }

    public double measure(RichTableData data, double width, int baseFontSize,
                          String fontFamily, double lineHeightCoef) {
        return compute(data, width, baseFontSize, fontFamily, lineHeightCoef).totalHeight();
    }

    public Layout compute(RichTableData data, double width, int baseFontSize,
                          String fontFamily, double lineHeightCoef, int depth) {
        if (data.cells().isEmpty() || data.colCount() == 0) {
            return new Layout(new double[]{0}, new double[]{0}, Map.of(), 0, 0);
        }

        double[] colWidths = resolveColWidths(data, width);
        double[] colOffsets = buildOffsets(colWidths);

        Map<RichTableCell, Double> naturalHeights = new HashMap<>();
        for (RichTableCell c : data.cells()) {
            double innerW = computeInnerWidth(c, colOffsets, data.colCount());
            double h = measureCellContent(c, innerW, baseFontSize, fontFamily, lineHeightCoef, depth);
            RichTableCellPadding pad = c.paddingOrDefault();

            /* Пустая ячейка в браузере всё равно занимает один line-box. */
            double minCellHeight = baseFontSize * lineHeightCoef + pad.top() + pad.bottom();

            naturalHeights.put(c,
                    Math.max(MIN_ROW_HEIGHT, Math.max(minCellHeight, h + pad.top() + pad.bottom())));
        }

        double[] rowHeights = resolveRowHeights(data.cells(), data.rowCount(), naturalHeights);
        double[] rowOffsets = buildOffsets(rowHeights);

        return new Layout(
                colOffsets,
                rowOffsets,
                naturalHeights,
                rowOffsets[rowOffsets.length - 1],
                colOffsets[colOffsets.length - 1]
        );
    }

    /**
     * Повторяем поведение браузера в `table-layout: fixed`:
     *   • колонки с шириной > 0 — фиксированные;
     *   • колонки с 0 (auto) — делят остаток поровну.
     */
    private double[] resolveColWidths(RichTableData data, double totalWidth) {
        int n = data.colCount();
        double[] cw = data.colWidths();

        if (cw == null || cw.length != n) {
            double[] out = new double[n];
            for (int i = 0; i < n; i++) out[i] = totalWidth / n;
            return out;
        }

        double totalExplicit = 0;
        int autoCount = 0;
        for (double w : cw) {
            if (w > 0) totalExplicit += w;
            else autoCount++;
        }

        double[] out = new double[n];

        if (totalExplicit == 0) {
            for (int i = 0; i < n; i++) out[i] = totalWidth / n;
            return out;
        }

        if (autoCount == 0) {
            for (int i = 0; i < n; i++) out[i] = cw[i] / totalExplicit * totalWidth;
            return out;
        }

        double remaining = Math.max(0, totalWidth - totalExplicit);
        double autoWidth = remaining / autoCount;
        for (int i = 0; i < n; i++) {
            out[i] = cw[i] > 0 ? cw[i] : autoWidth;
        }
        return out;
    }

    private double[] buildOffsets(double[] widths) {
        double[] off = new double[widths.length + 1];
        for (int i = 0; i < widths.length; i++) off[i + 1] = off[i] + widths[i];
        return off;
    }

    private double[] resolveRowHeights(List<RichTableCell> cells, int rowCount,
                                       Map<RichTableCell, Double> naturalHeights) {
        double[] rowHeights = new double[rowCount];
        for (int i = 0; i < rowCount; i++) rowHeights[i] = MIN_ROW_HEIGHT;

        for (RichTableCell c : cells) {
            if (c.rowspan() != 1) continue;
            double need = naturalHeights.getOrDefault(c, 0.0);
            if (need > rowHeights[c.rowIndex()]) rowHeights[c.rowIndex()] = need;
        }

        for (int iter = 0; iter < 10; iter++) {
            boolean changed = false;
            for (RichTableCell c : cells) {
                if (c.rowspan() <= 1) continue;
                double need = naturalHeights.getOrDefault(c, 0.0);
                double have = 0;
                for (int r = c.rowIndex(); r < c.rowIndex() + c.rowspan(); r++) {
                    if (r < rowHeights.length) have += rowHeights[r];
                }
                if (have + 0.5 < need) {
                    double delta = (need - have) / c.rowspan();
                    for (int r = c.rowIndex(); r < c.rowIndex() + c.rowspan(); r++) {
                        if (r < rowHeights.length) rowHeights[r] += delta;
                    }
                    changed = true;
                }
            }
            if (!changed) break;
        }
        return rowHeights;
    }

    private double computeInnerWidth(RichTableCell c, double[] colOffsets, int colCount) {
        int start = c.colIndex();
        int end = Math.min(c.colIndex() + c.colspan(), colCount);
        double cw = colOffsets[end] - colOffsets[start];
        RichTableCellPadding pad = c.paddingOrDefault();
        return Math.max(10, cw - pad.left() - pad.right());
    }

    private double measureCellContent(RichTableCell c, double innerW,
                                      int baseFontSize, String fontFamily,
                                      double lineHeightCoef, int depth) {
        double total = 0;
        int n = 0;
        for (RichBlock b : c.blocks()) {
            if (b instanceof RichBlock.Text tb) {
                total += measureTextHeight(tb.runs(), innerW, baseFontSize, fontFamily, lineHeightCoef);
            } else if (b instanceof RichBlock.Table tb2) {
                if (depth + 1 <= MAX_DEPTH) {
                    total += compute(tb2.data(), innerW, baseFontSize, fontFamily,
                            lineHeightCoef, depth + 1).totalHeight();
                }
            }
            n++;
        }
        if (n > 1) total += (n - 1) * CELL_BLOCK_MARGIN;
        return total;
    }

    private double measureTextHeight(List<RichRun> runs, double innerW,
                                     int baseFontSize, String fontFamily,
                                     double lineHeightCoef) {
        double coef = lineHeightCoef > 0 ? lineHeightCoef : DEFAULT_LINE_HEIGHT_COEF;
        List<RichLine> lines = RichTextLayout.layoutText(
                runs, innerW, baseFontSize, fontFamily, coef, measure
        );
        if (lines.isEmpty()) return 0;
        double h = 0;
        for (RichLine l : lines) h = Math.max(h, l.y() + l.height());
        return h;
    }

    public record Layout(
            double[] colOffsets,
            double[] rowOffsets,
            Map<RichTableCell, Double> naturalHeights,
            double totalHeight,
            double totalWidth
    ) {}
}