package ru.ticket.text.richtext;

import ru.ticket.svg.SvgContext;
import ru.ticket.util.XmlEscape;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Пишет текст по дуге в SVG для frontend-элемента TextElement
 * (textMode = "arc").
 *
 * Геометрия совпадает с frontend drawArcText (lib/stamps/render.ts):
 *   cx = width / 2
 *   cy = radius + height / 2
 *   startDeg / endDeg — из el.arcStartAngle / el.arcEndAngle
 *
 * Позиционирование: <textPath> с startOffset="50%" и text-anchor="middle"
 * даёт текст, отцентрованный по дуге — как ожидает пользователь.
 */
@Component
public class ArcTextSvgWriter {

    /**
     * Пишет arc-текст внутрь уже открытого <g> (после translate/rotate).
     *
     * @param out        буфер body
     * @param ctx        SVG-контекст (для defs и counter'а id)
     * @param text       сам текст
     * @param cx         центр окружности в локальных координатах группы
     * @param cy         центр окружности
     * @param radius     радиус
     * @param startDeg   начальный угол (может быть > endDeg при flip)
     * @param endDeg     конечный угол
     * @param fontSize   размер шрифта
     * @param fontFamily семейство
     * @param fontStyle  normal | bold | italic | italic bold
     * @param fill       цвет
     */
    public void write(StringBuilder out,
                      SvgContext ctx,
                      String text,
                      double cx,
                      double cy,
                      double radius,
                      double startDeg,
                      double endDeg,
                      double fontSize,
                      String fontFamily,
                      String fontStyle,
                      String fill) {
        if (text == null || text.isEmpty()) return;
        if (radius <= 0) return;

        String pathId = ctx.defs().nextId("arc-text");

        String d = buildArcPath(cx, cy, radius, startDeg, endDeg);
        ctx.appendDefs(String.format(Locale.ROOT,
                "<path id=\"%s\" d=\"%s\" fill=\"none\"/>", pathId, d));

        String fontWeight = isBold(fontStyle) ? "700" : "400";
        String fontStyleAttr = isItalic(fontStyle) ? "italic" : "normal";

        out.append(String.format(Locale.ROOT,
                "<text font-family=\"%s\" font-size=\"%.2f\" font-weight=\"%s\" " +
                        "font-style=\"%s\" fill=\"%s\" xml:space=\"preserve\">\n" +
                        "  <textPath href=\"#%s\" xlink:href=\"#%s\" startOffset=\"50%%\" " +
                        "text-anchor=\"middle\">%s</textPath>\n" +
                        "</text>\n",
                XmlEscape.attr(fontFamily == null ? "Arial" : fontFamily),
                fontSize, fontWeight, fontStyleAttr,
                fill == null ? "#000000" : fill,
                pathId, pathId,
                XmlEscape.escape(text)));
    }

    private String buildArcPath(double cx, double cy, double radius,
                                double startDeg, double endDeg) {
        double sa = Math.toRadians(startDeg);
        double ea = Math.toRadians(endDeg);
        double sx = cx + radius * Math.cos(sa);
        double sy = cy + radius * Math.sin(sa);
        double ex = cx + radius * Math.cos(ea);
        double ey = cy + radius * Math.sin(ea);

        int largeArc = Math.abs(endDeg - startDeg) > 180 ? 1 : 0;
        int sweep = (endDeg > startDeg) ? 1 : 0;

        return String.format(Locale.ROOT,
                "M %.2f %.2f A %.2f %.2f 0 %d %d %.2f %.2f",
                sx, sy, radius, radius, largeArc, sweep, ex, ey);
    }

    private boolean isBold(String style)   { return style != null && style.contains("bold"); }
    private boolean isItalic(String style) { return style != null && style.contains("italic"); }
}