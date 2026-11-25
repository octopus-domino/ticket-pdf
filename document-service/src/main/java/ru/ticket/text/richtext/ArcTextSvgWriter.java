package ru.ticket.text.richtext;

import ru.ticket.svg.SvgContext;
import ru.ticket.util.XmlEscape;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Пишет arc-текст (TextElement.textMode = "arc") для SVG.
 * Повторяет drawArcText с фронта: посимвольно, каждый символ
 * поворачивается по касательной к эллипсу, равномерно по длине дуги.
 */
@Component
public class ArcTextSvgWriter {

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

        String fontWeight = isBold(fontStyle) ? "700" : "400";
        String fontStyleAttr = isItalic(fontStyle) ? "italic" : "normal";

        double a0 = Math.toRadians(startDeg);
        double a1 = Math.toRadians(endDeg);
        int n = text.length();
        double rx = radius;
        double ry = radius;

        double totalLen = ellipseArcLength(rx, ry, a0, a1);
        if (totalLen <= 0) return;

        String safeFamily = XmlEscape.attr(fontFamily == null ? "Arial" : fontFamily);
        String safeFill = fill == null ? "#000000" : fill;

        for (int i = 0; i < n; i++) {
            char ch = text.charAt(i);
            if (ch == ' ' || ch == '\t' || ch == '\n') continue;

            double fraction = n > 1 ? (i + 0.5) / n : 0.5;
            double a = angleAtArcFraction(rx, ry, a0, a1, fraction);
            double cxA = cx + Math.cos(a) * rx;
            double cyA = cy + Math.sin(a) * ry;
            double tx = -rx * Math.sin(a);
            double ty = ry * Math.cos(a);
            double rot = Math.toDegrees(Math.atan2(ty, tx));

            out.append(String.format(Locale.ROOT,
                    "<text x=\"0\" y=\"0\" font-family=\"%s\" font-size=\"%.2f\" " +
                            "font-weight=\"%s\" font-style=\"%s\" fill=\"%s\" " +
                            "text-anchor=\"middle\" dominant-baseline=\"middle\" " +
                            "transform=\"translate(%.2f %.2f) rotate(%.2f)\" " +
                            "xml:space=\"preserve\">%s</text>\n",
                    safeFamily, fontSize, fontWeight, fontStyleAttr, safeFill,
                    cxA, cyA, rot,
                    XmlEscape.escape(String.valueOf(ch))));
        }
    }

    /* ============================================================
       Тот же алгоритм, что lib/stamps/render.ts
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