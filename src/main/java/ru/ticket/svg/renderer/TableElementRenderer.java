package ru.ticket.svg.renderer;

import ru.ticket.dto.TableElement;
import ru.ticket.svg.SvgContext;
import ru.ticket.text.TextLayoutService;
import ru.ticket.util.XmlEscape;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class TableElementRenderer implements ElementRenderer<TableElement> {

    private static final double CELL_PADDING = 4;

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

        double cw = tb.width / tb.cols;
        double ch = tb.height / tb.rows;

        String tr = tb.rotation != 0
                ? String.format(Locale.ROOT, " transform=\"rotate(%.2f %.2f %.2f)\"",
                tb.rotation, tb.x, tb.y)
                : "";

        out.append("<g").append(tr).append(">\n");

        for (int r = 0; r < tb.rows; r++) {
            for (int c = 0; c < tb.cols; c++) {
                String bg = (r == 0) ? tb.headerBg : "#ffffff";
                out.append(String.format(Locale.ROOT,
                        "<rect x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\" " +
                                "fill=\"%s\" stroke=\"%s\" stroke-width=\"1\"/>\n",
                        tb.x + c * cw, tb.y + r * ch, cw, ch, bg, tb.borderColor));

                String txt = cellText(tb, r, c);
                if (!txt.isEmpty()) {
                    renderCellText(out, tb, r, c, cw, ch, txt);
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

    private void renderCellText(StringBuilder out, TableElement tb,
                                int r, int c, double cw, double ch, String txt) {
        double innerW = cw - CELL_PADDING * 2;
        List<String> lines = textLayout.wrap(
                txt, innerW, "Arial", (int) Math.round(tb.fontSize), "normal");
        double lineH = tb.fontSize * 1.2;
        double startY = tb.y + r * ch + CELL_PADDING + tb.fontSize;

        for (int i = 0; i < lines.size(); i++) {
            out.append(String.format(Locale.ROOT,
                    "<text x=\"%.2f\" y=\"%.2f\" font-size=\"%.2f\" fill=\"#111\" " +
                            "xml:space=\"preserve\">%s</text>\n",
                    tb.x + c * cw + CELL_PADDING,
                    startY + i * lineH,
                    tb.fontSize,
                    XmlEscape.escape(lines.get(i))));
        }
    }
}