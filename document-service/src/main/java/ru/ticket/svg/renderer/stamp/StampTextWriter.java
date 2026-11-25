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

    /* ============================================================
       Straight — без изменений
       ============================================================ */

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

    /* ============================================================
       Arc — посимвольно, как drawArcText на канве.
       Каждый символ поворачивается по касательной к эллипсу.
       ============================================================ */

    private void writeArc(StringBuilder out, StampTextItem t, SvgContext ctx) {
        double rx = t.arcRx != null ? t.arcRx : t.arcRadius;
        double ry = t.arcRy != null ? t.arcRy : t.arcRadius;

        String text = t.text == null ? "" : t.text;
        if (text.isEmpty()) return;

        String fontWeight = isBold(t.fontStyle) ? "700" : "400";
        String fontStyle  = isItalic(t.fontStyle) ? "italic" : "normal";

        double a0 = Math.toRadians(t.arcStartDeg);
        double a1 = Math.toRadians(t.arcEndDeg);
        int n = text.length();

        double totalLen = ellipseArcLength(rx, ry, a0, a1);
        if (totalLen <= 0) return;

        for (int i = 0; i < n; i++) {
            char ch = text.charAt(i);
            if (ch == ' ' || ch == '\t' || ch == '\n') continue;

            double fraction = n > 1 ? (i + 0.5) / n : 0.5;
            double a = angleAtArcFraction(rx, ry, a0, a1, fraction);
            double cxA = t.arcCx + Math.cos(a) * rx;
            double cyA = t.arcCy + Math.sin(a) * ry;
            // Касательная к эллипсу: d/dt(rx·cos t, ry·sin t) = (-rx·sin t, ry·cos t)
            double direction = Math.signum(a1 - a0);
            double tx = direction * -rx * Math.sin(a);
            double ty = direction * ry * Math.cos(a);
            double rot = Math.toDegrees(Math.atan2(ty, tx));

            out.append(String.format(Locale.ROOT,
                    "<text x=\"0\" y=\"0\" font-family=\"%s\" font-size=\"%.2f\" " +
                            "font-weight=\"%s\" font-style=\"%s\" fill=\"%s\" " +
                            "text-anchor=\"middle\" dominant-baseline=\"middle\" " +
                            "transform=\"translate(%.2f %.2f) rotate(%.2f)\" " +
                            "xml:space=\"preserve\">%s</text>\n",
                    XmlEscape.attr(t.fontFamily), t.fontSize,
                    fontWeight, fontStyle, t.fill,
                    cxA, cyA, rot,
                    XmlEscape.escape(String.valueOf(ch))));
        }
    }

    /* ============================================================
       Эллиптическая дуга — то же, что lib/stamps/render.ts
       ============================================================ */

    private static double ellipseArcLength(double rx, double ry, double a0, double a1) {
        int N = 64;
        double h = (a1 - a0) / N;
        double sum = 0;
        for (int i = 0; i <= N; i++) {
            double a = a0 + i * h;
            double speed = Math.hypot(rx * Math.sin(a), ry * Math.cos(a));
            double w = (i == 0 || i == N) ? 1 : (i % 2 == 0 ? 2 : 4);
            sum += w * speed;
        }
        return Math.abs((h / 3) * sum);
    }

    private static double angleAtArcFraction(double rx, double ry,
                                             double aStart, double aEnd,
                                             double fraction) {
        double total = ellipseArcLength(rx, ry, aStart, aEnd);
        if (total == 0) return aStart;
        double target = total * fraction;
        double lo = 0, hi = 1;
        for (int it = 0; it < 40; it++) {
            double mid = (lo + hi) / 2;
            double a = aStart + (aEnd - aStart) * mid;
            double len = ellipseArcLength(rx, ry, aStart, a);
            if (len < target) lo = mid; else hi = mid;
        }
        return aStart + (aEnd - aStart) * (lo + hi) / 2;
    }

    private boolean isBold(String style)   { return style != null && style.contains("bold"); }
    private boolean isItalic(String style) { return style != null && style.contains("italic"); }
}
