package ru.ticket.text.richtext;

import ru.ticket.text.TextLayoutService;
import ru.ticket.util.XmlEscape;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class TableSvgRenderer {

    private final TableLayoutService layout;
    private final TextLayoutService textLayout;

    public TableSvgRenderer(TableLayoutService layout, TextLayoutService textLayout) {
        this.layout = layout;
        this.textLayout = textLayout;
    }

    public void render(StringBuilder out, RichTableData data,
                       double x, double y, double width,
                       int baseFontSize, String baseFontFamily,
                       String lineHeightCoefDummy,
                       double lineHeightCoef) {
        if (data.cells().isEmpty()) return;

        TableLayoutService.Layout tl = layout.compute(
                data, width, baseFontSize, baseFontFamily, lineHeightCoef);
        double[] co = tl.colOffsets();
        double[] ro = tl.rowOffsets();

        /* 1. Фоны ячеек (без рамок) */
        for (RichTableCell c : data.cells()) {
            double cx = co[c.colIndex()];
            double cy = ro[c.rowIndex()];
            int cEnd = Math.min(c.colIndex() + c.colspan(), co.length - 1);
            int rEnd = Math.min(c.rowIndex() + c.rowspan(), ro.length - 1);
            double cw = co[cEnd] - cx;
            double ch = ro[rEnd] - cy;

            String fill = resolveFill(c);
            if ("none".equals(fill)) continue;

            out.append(String.format(Locale.ROOT,
                    "<rect x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\" fill=\"%s\"/>\n",
                    x + cx, y + cy, cw, ch, fill));
        }

        /* 2. Границы (только те стороны, что явно заданы в HTML) */
        for (RichTableCell c : data.cells()) {
            renderBorders(out, c, co, ro, x, y);
        }

        /* 3. Содержимое ячеек */
        for (RichTableCell c : data.cells()) {
            renderCell(out, c, co, ro, x, y, baseFontSize, baseFontFamily, lineHeightCoef);
        }
    }

    private void renderBorders(StringBuilder out, RichTableCell c,
                               double[] co, double[] ro,
                               double tx, double ty) {
        double cx = co[c.colIndex()];
        double cy = ro[c.rowIndex()];
        int cEnd = Math.min(c.colIndex() + c.colspan(), co.length - 1);
        int rEnd = Math.min(c.rowIndex() + c.rowspan(), ro.length - 1);
        double cw = co[cEnd] - cx;
        double ch = ro[rEnd] - cy;

        double x1 = tx + cx;
        double y1 = ty + cy;
        double x2 = x1 + cw;
        double y2 = y1 + ch;

        if (c.hasAnyVisibleBorder()) {
            appendSide(out, c.borderTop(),    x1, y1, x2, y1);
            appendSide(out, c.borderRight(),  x2, y1, x2, y2);
            appendSide(out, c.borderBottom(), x1, y2, x2, y2);
            appendSide(out, c.borderLeft(),   x1, y1, x1, y2);
            return;
        }

        if (c.hasExplicitBorders()) {
            return;
        }

        String color = c.borderColor() != null ? c.borderColor() : "#cbd5e1";
        double width = c.borderWidth() != null ? c.borderWidth() : 1.0;
        String style = c.borderStyle() != null ? c.borderStyle() : "solid";

        if (width <= 0 || "none".equalsIgnoreCase(style)) return;

        String dash = dashForStyle(style);
        appendLine(out, x1, y1, x2, y1, color, width, dash);
        appendLine(out, x2, y1, x2, y2, color, width, dash);
        appendLine(out, x1, y2, x2, y2, color, width, dash);
        appendLine(out, x1, y1, x1, y2, color, width, dash);
    }

    private void appendSide(StringBuilder out, BorderSide side,
                            double x1, double y1, double x2, double y2) {
        if (side == null || !side.isVisible()) return;

        String color = side.color() != null ? side.color() : "#000000";
        String dash = dashForStyle(side.style());

        appendLine(out, x1, y1, x2, y2, color, side.width(), dash);
    }

    private void appendLine(StringBuilder out,
                            double x1, double y1, double x2, double y2,
                            String color, double width, String dash) {
        out.append(String.format(Locale.ROOT,
                "<line x1=\"%.2f\" y1=\"%.2f\" x2=\"%.2f\" y2=\"%.2f\" " +
                        "stroke=\"%s\" stroke-width=\"%.2f\"%s/>\n",
                x1, y1, x2, y2, color, width, dash));
    }

    private String dashForStyle(String style) {
        if (style == null) return "";
        return switch (style.toLowerCase(Locale.ROOT)) {
            case "dashed" -> " stroke-dasharray=\"4,4\"";
            case "dotted" -> " stroke-dasharray=\"2,3\"";
            default -> "";
        };
    }

    private String resolveFill(RichTableCell c) {
        String raw = c.bgColor();
        if (raw == null || raw.isBlank()) {
            // ВАЖНО: для обычных ячеек возвращаем "none", чтобы не перекрывать
            // элементы на заднем плане (зеленую/серую плашку).
            return c.isHeader() ? "#f1f5f9" : "none";
        }
        if (isTransparentLike(raw)) return "none";
        return raw;
    }

    private boolean isTransparentLike(String v) {
        if (v == null) return false;
        String s = v.trim().toLowerCase(Locale.ROOT);
        return s.equals("transparent")
                || s.equals("none")
                || s.equals("inherit")
                || s.equals("initial")
                || s.equals("rgba(0,0,0,0)")
                || s.equals("rgba(0, 0, 0, 0)");
    }

    private void renderCell(StringBuilder out, RichTableCell c,
                            double[] co, double[] ro,
                            double tx, double ty,
                            int baseFontSize, String baseFontFamily,
                            double lineHeightCoef) {
        RichTableCellPadding pad = c.paddingOrDefault();

        double cx = co[c.colIndex()];
        double cy = ro[c.rowIndex()];
        int cEnd = Math.min(c.colIndex() + c.colspan(), co.length - 1);
        int rEnd = Math.min(c.rowIndex() + c.rowspan(), ro.length - 1);
        double cw = co[cEnd] - cx;
        double ch = ro[rEnd] - cy;

        double innerX = tx + cx + pad.left();
        double innerY = ty + cy + pad.top();
        double innerW = Math.max(10, cw - pad.left() - pad.right());
        double innerH = Math.max(10, ch - pad.top() - pad.bottom());

        RichTextLayout.LayoutResult inner = RichTextLayout.layout(
                c.blocks(), innerW, baseFontSize, baseFontFamily, lineHeightCoef,
                textLayout, layout
        );
        if (inner.blocks().isEmpty()) return;

        double contentH = inner.totalHeight();
        double offsetY;
        switch (c.verticalAlign() == null ? "top" : c.verticalAlign()) {
            case "middle" -> offsetY = innerY + Math.max(0, (innerH - contentH) / 2);
            case "bottom" -> offsetY = innerY + Math.max(0, innerH - contentH);
            default        -> offsetY = innerY;
        }

        String align = c.align() == null ? "left" : c.align();

        for (RichTextLayout.PositionedBlock pb : inner.blocks()) {
            double blockY = offsetY + pb.y();
            if (pb instanceof RichTextLayout.TextBlock tbk) {
                for (RichLine line : tbk.lines()) {
                    double lineOffsetX = 0;
                    if ("center".equals(align)) {
                        lineOffsetX = Math.max(0, (innerW - line.width()) / 2);
                    } else if ("right".equals(align)) {
                        lineOffsetX = Math.max(0, innerW - line.width());
                    }

                    renderLine(out, line, innerX + lineOffsetX, blockY);
                }
            } else if (pb instanceof RichTextLayout.TableBlock tbk) {
                render(out, tbk.data(), innerX, blockY, innerW,
                        baseFontSize, baseFontFamily, align, lineHeightCoef);
            }
        }
    }

    private void renderLine(StringBuilder out, RichLine line,
                            double baseX, double baseY) {
        for (PositionedRun run : line.runs()) {
            double x = baseX + run.x();
            double y = baseY + run.y();

            String color = run.color() != null ? run.color() : "#111";
            String fontWeight = run.bold() ? "700" : "400";
            String fontStyle = run.italic() ? "italic" : "normal";

            StringBuilder td = new StringBuilder();
            if (run.underline()) td.append("underline ");
            if (run.strike()) td.append("line-through");
            String decoration = td.toString().trim();

            out.append(String.format(Locale.ROOT,
                    "<text x=\"%.2f\" y=\"%.2f\" font-size=\"%.2f\" font-family=\"%s\" " +
                            "font-weight=\"%s\" font-style=\"%s\"%s fill=\"%s\" " +
                            "xml:space=\"preserve\">%s</text>\n",
                    x, y, (double) run.fontSize(),
                    XmlEscape.attr(run.fontFamily()),
                    fontWeight, fontStyle,
                    decoration.isEmpty()
                            ? ""
                            : " text-decoration=\"" + decoration + "\"",
                    color, XmlEscape.escape(run.text())
            ));
        }
    }
}