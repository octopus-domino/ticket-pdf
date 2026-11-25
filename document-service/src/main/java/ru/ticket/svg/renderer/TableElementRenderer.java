package ru.ticket.svg.renderer;

import ru.ticket.dto.TableElement;
import ru.ticket.svg.SvgContext;
import ru.ticket.text.FontFamilyResolver;
import ru.ticket.text.TextLayoutService;
import ru.ticket.util.XmlEscape;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class TableElementRenderer implements ElementRenderer<TableElement> {

    private final TextLayoutService textLayout;

    public TableElementRenderer(TextLayoutService textLayout) {
        this.textLayout = textLayout;
    }

    @Override
    public Class<TableElement> supports() { return TableElement.class; }

    @Override
    public void render(TableElement tb, SvgContext ctx) {
        if (tb.cols <= 0 || tb.rows <= 0) return;

        StringBuilder out = ctx.body();

        double[] columns = axis(tb.width, tb.cols, tb.columnWidths);
        double[] rows = axis(tb.height, tb.rows, tb.rowHeights);

        String tr = tb.rotation != 0
                ? String.format(Locale.ROOT, " transform=\"rotate(%.2f %.2f %.2f)\"",
                tb.rotation, tb.x, tb.y)
                : "";

        out.append("<g").append(tr).append(">\n");

        for (int r = 0; r < tb.rows; r++) {
            for (int c = 0; c < tb.cols; c++) {
                var style = cellStyle(tb, r, c);
                String bg = style != null && style.background() != null ? style.background()
                        : (r == 0) ? tb.headerBg : "#ffffff";
                double x = tb.x + columns[c], y = tb.y + rows[r];
                double cw = columns[c + 1] - columns[c], ch = rows[r + 1] - rows[r];
                out.append(String.format(Locale.ROOT,
                        "<rect x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\" " +
                                "fill=\"%s\" stroke=\"%s\" stroke-width=\"1\"/>\n",
                        x, y, cw, ch, XmlEscape.attr(bg), XmlEscape.attr(tb.borderColor)));

                String txt = cellText(tb, r, c);
                if (!txt.isEmpty()) {
                    renderCellText(ctx, tb, style, x, y, cw, ch, txt);
                }
            }
        }

        out.append("</g>\n");
    }

    private String cellText(TableElement tb, int r, int c) {
        if (tb.data == null || r >= tb.data.size()) return "";
        List<String> row = tb.data.get(r);
        if (row == null || c >= row.size()) return "";
        String v = row.get(c);
        return v == null ? "" : v;
    }

    private static double[] axis(double total, int count, List<Double> weights) {
        boolean valid = weights != null && weights.size() == count &&
                weights.stream().allMatch(weight -> weight != null && Double.isFinite(weight) && weight > 0);
        double sum = valid ? weights.stream().mapToDouble(Double::doubleValue).sum() : count;
        double[] offsets = new double[count + 1];
        for (int i = 0; i < count; i++) {
            offsets[i + 1] = offsets[i] + total * (valid ? weights.get(i) : 1) / sum;
        }
        return offsets;
    }

    private static TableElement.CellStyle cellStyle(TableElement table, int row, int column) {
        if (table.cellStyles == null || row >= table.cellStyles.size()) return null;
        var styles = table.cellStyles.get(row);
        return styles == null || column >= styles.size() ? null : styles.get(column);
    }

    private void renderCellText(SvgContext ctx, TableElement tb, TableElement.CellStyle style,
                                double x, double y, double cw, double ch, String txt) {
        double size = style != null && style.fontSize() != null && style.fontSize() > 0 ? style.fontSize() : tb.fontSize;
        if (!Double.isFinite(size) || size <= 0) return;
        double padding = tb.cellPadding != null && Double.isFinite(tb.cellPadding) ? Math.max(0, tb.cellPadding) : 6;
        String requested = style != null && style.fontFamily() != null && !style.fontFamily().isBlank() ? style.fontFamily() : "Arial";
        String family = FontFamilyResolver.available(requested) ? FontFamilyResolver.resolve(requested) : "DejaVu Sans";
        String fontStyle = style != null && style.fontStyle() != null ? style.fontStyle().toLowerCase(Locale.ROOT) : "normal";
        String color = style != null && style.color() != null ? style.color() : "#111";
        double width = cw - padding * 2, height = ch - padding * 2;
        if (width <= 0 || height <= 0) return;
        int fontSize = Math.max(1, (int) Math.round(size));
        List<String> lines = textLayout.wrap(txt, width, family, fontSize, fontStyle);
        String clipId = ctx.defs().nextId("table-cell");
        ctx.appendDefs(String.format(Locale.ROOT,
                "<clipPath id=\"%s\"><rect x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\"/></clipPath>",
                clipId, x + padding, y + padding, width, height));
        var out = ctx.body();
        out.append("<g clip-path=\"url(#").append(clipId).append(")\">\n");
        double startY = y + padding + size * 0.8;
        for (int i = 0; i < lines.size() && (i == 0 || (i + 1) * size <= height); i++) {
            out.append(String.format(Locale.ROOT,
                    "<text x=\"%.2f\" y=\"%.2f\" font-size=\"%.2f\" font-family=\"%s\" font-weight=\"%s\" font-style=\"%s\" fill=\"%s\" " +
                            "xml:space=\"preserve\">%s</text>\n",
                    x + padding, startY + i * size, size, XmlEscape.attr(family), fontStyle.contains("bold") ? "bold" : "normal",
                    fontStyle.contains("italic") ? "italic" : "normal", XmlEscape.attr(color), XmlEscape.escape(lines.get(i))));
        }
        out.append("</g>\n");
    }
}
