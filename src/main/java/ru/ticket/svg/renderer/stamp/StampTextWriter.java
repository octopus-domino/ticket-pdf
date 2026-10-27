package ru.ticket.svg.renderer.stamp;

import ru.ticket.dto.StampTextItem;
import ru.ticket.svg.SvgContext;
import ru.ticket.text.TextLayoutService;
import ru.ticket.util.XmlEscape;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class StampTextWriter {

    private final TextLayoutService textLayout;

    public StampTextWriter(TextLayoutService textLayout) {
        this.textLayout = textLayout;
    }

    public void write(StringBuilder out, StampTextItem t, SvgContext ctx) {
        if ("straight".equals(t.mode)) {
            writeStraight(out, t);
        } else {
            writeArc(out, t, ctx);
        }
    }

    private void writeStraight(StringBuilder out, StampTextItem t) {
        String anchor = "center".equals(t.align) ? "middle"
                : "right".equals(t.align)  ? "end"
                : "start";
        double tx = t.x + ("center".equals(t.align) ? t.width / 2
                : "right".equals(t.align)  ? t.width : 0);

        String fontWeight = isBold(t.fontStyle) ? "700" : "400";
        String fontStyle  = isItalic(t.fontStyle) ? "italic" : "normal";

        List<String> lines = textLayout.wrap(
                t.text, t.width, t.fontFamily, (int) Math.round(t.fontSize), t.fontStyle);
        double lineHeight = t.fontSize * 1.2;

        for (int i = 0; i < lines.size(); i++) {
            out.append(String.format(Locale.ROOT,
                    "<text x=\"%.2f\" y=\"%.2f\" font-size=\"%.2f\" " +
                            "font-family=\"%s\" font-weight=\"%s\" font-style=\"%s\" fill=\"%s\" " +
                            "text-anchor=\"%s\" dominant-baseline=\"middle\" xml:space=\"preserve\">%s</text>\n",
                    tx, t.y + t.fontSize / 2.0 + i * lineHeight, t.fontSize,
                    XmlEscape.attr(t.fontFamily), fontWeight, fontStyle, t.fill,
                    anchor, XmlEscape.escape(lines.get(i))));
        }
    }

    private void writeArc(StringBuilder out, StampTextItem t, SvgContext ctx) {
        double rx = t.arcRx != null ? t.arcRx : t.arcRadius;
        double ry = t.arcRy != null ? t.arcRy : t.arcRadius;

        String pathId = ctx.defs().nextId("arc-" + t.id);
        String d = buildArcPath(t.arcCx, t.arcCy, rx, ry, t.arcStartDeg, t.arcEndDeg);

        ctx.appendDefs(String.format(Locale.ROOT,
                "<path id=\"%s\" d=\"%s\" fill=\"none\"/>", pathId, d));

        String fontWeight = isBold(t.fontStyle) ? "700" : "400";
        String fontStyle  = isItalic(t.fontStyle) ? "italic" : "normal";

        out.append(String.format(Locale.ROOT,
                "<text font-family=\"%s\" font-size=\"%.2f\" font-weight=\"%s\" " +
                        "font-style=\"%s\" fill=\"%s\" xml:space=\"preserve\">\n" +
                        "  <textPath href=\"#%s\" xlink:href=\"#%s\" startOffset=\"50%%\" " +
                        "text-anchor=\"middle\">%s</textPath>\n" +
                        "</text>\n",
                XmlEscape.attr(t.fontFamily), t.fontSize, fontWeight, fontStyle,
                t.fill, pathId, pathId, XmlEscape.escape(t.text)));
    }

    private String buildArcPath(double cx, double cy, double rx, double ry,
                                double startDeg, double endDeg) {
        double sa = Math.toRadians(startDeg);
        double ea = Math.toRadians(endDeg);
        double sx = cx + rx * Math.cos(sa);
        double sy = cy + ry * Math.sin(sa);
        double ex = cx + rx * Math.cos(ea);
        double ey = cy + ry * Math.sin(ea);

        int largeArc = Math.abs(endDeg - startDeg) > 180 ? 1 : 0;
        int sweep = (endDeg > startDeg) ? 1 : 0;

        return String.format(Locale.ROOT,
                "M %.2f %.2f A %.2f %.2f 0 %d %d %.2f %.2f",
                sx, sy, rx, ry, largeArc, sweep, ex, ey);
    }

    private boolean isBold(String style)   { return style != null && style.contains("bold"); }
    private boolean isItalic(String style) { return style != null && style.contains("italic"); }
}