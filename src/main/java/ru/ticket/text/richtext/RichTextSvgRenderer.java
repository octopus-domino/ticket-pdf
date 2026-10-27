package ru.ticket.text.richtext;

import ru.ticket.dto.Padding;
import ru.ticket.dto.TextElement;
import ru.ticket.text.TextLayoutService;
import ru.ticket.util.XmlEscape;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class RichTextSvgRenderer {

    private final RichTextParser parser;
    private final TextLayoutService textLayout;
    private final TableLayoutService tableLayout;
    private final TableSvgRenderer tableRenderer;

    public RichTextSvgRenderer(RichTextParser parser,
                               TextLayoutService textLayout,
                               TableLayoutService tableLayout,
                               TableSvgRenderer tableRenderer) {
        this.parser = parser;
        this.textLayout = textLayout;
        this.tableLayout = tableLayout;
        this.tableRenderer = tableRenderer;
    }

    public void render(StringBuilder out, TextElement t) {
        Padding pad = t.padding != null ? t.padding : new Padding();
        double innerX = pad.left;
        double innerY = pad.top;
        double innerW = Math.max(10, t.width - pad.left - pad.right);
        double innerH = Math.max(10, t.height - pad.top - pad.bottom);

        List<RichBlock> blocks = parser.parse(t.html);
        if (blocks.isEmpty()) return;

        double lineHeightCoef = t.lineHeight > 0 ? t.lineHeight : 1.5;
        int baseFontSize = (int) Math.round(t.fontSize);

        RichTextLayout.LayoutResult layoutRes = RichTextLayout.layout(
                blocks, innerW, baseFontSize, t.fontFamily, lineHeightCoef,
                textLayout, tableLayout
        );
        if (layoutRes.blocks().isEmpty()) return;

        double totalHeight = layoutRes.totalHeight();
        double startY;
        switch (t.verticalAlign == null ? "top" : t.verticalAlign) {
            case "middle" -> startY = innerY + Math.max(0, (innerH - totalHeight) / 2);
            case "bottom" -> startY = innerY + Math.max(0, innerH - totalHeight);
            default        -> startY = innerY;
        }

        String align = t.align == null ? "left" : t.align;

        for (RichTextLayout.PositionedBlock pb : layoutRes.blocks()) {
            double blockY = startY + pb.y();
            if (pb instanceof RichTextLayout.TextBlock tbk) {
                renderTextBlock(out, t, tbk.lines(), innerX, blockY, innerW, align);
            } else if (pb instanceof RichTextLayout.TableBlock tbk) {
                tableRenderer.render(out, tbk.data(), innerX, blockY, innerW,
                        baseFontSize, t.fontFamily, align, lineHeightCoef);
            }
        }
    }

    private void renderTextBlock(StringBuilder out, TextElement t,
                                 List<RichLine> lines, double baseX, double baseY,
                                 double innerW, String align) {
        for (RichLine line : lines) {
            double offsetX = 0;
            if ("center".equals(align)) offsetX = Math.max(0, (innerW - line.width()) / 2);
            else if ("right".equals(align)) offsetX = Math.max(0, innerW - line.width());
            renderLine(out, t, line, baseX + offsetX, baseY);
        }
    }

    private void renderLine(StringBuilder out, TextElement t,
                            RichLine line, double baseX, double baseY) {
        for (PositionedRun run : line.runs()) {
            double x = baseX + run.x();
            double y = baseY + run.y();

            if (run.bgColor() != null && !run.bgColor().isBlank()) {
                double bgY = baseY + line.y();
                double bgH = line.height();
                out.append(String.format(Locale.ROOT,
                        "<rect x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\" fill=\"%s\"/>\n",
                        x, bgY, run.width(), bgH, run.bgColor()));
            }

            String color = run.color() != null ? run.color() : t.fill;
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
                    decoration.isEmpty() ? "" : " text-decoration=\"" + decoration + "\"",
                    color, XmlEscape.escape(run.text())
            ));
        }
    }
}