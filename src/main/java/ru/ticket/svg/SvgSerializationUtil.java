package ru.ticket.svg;

import ru.ticket.util.XmlEscape;

import java.util.Locale;

/**
 * Утилиты сериализации SVG-примитивов.
 * Используются несколькими рендерами — вынесены отдельно.
 */
public final class SvgSerializationUtil {

    private SvgSerializationUtil() {}

    public static String imageTag(String url, double x, double y,
                                  double w, double h, String extra) {
        String u = XmlEscape.attr(url);
        return String.format(Locale.ROOT,
                "<image href=\"%s\" xlink:href=\"%s\" x=\"%.2f\" y=\"%.2f\" " +
                        "width=\"%.2f\" height=\"%.2f\"%s/>\n",
                u, u, x, y, w, h, extra == null ? "" : extra);
    }

    public static String dashAttr(String dash) {
        if (dash == null) return "";
        return switch (dash) {
            case "dashed"   -> " stroke-dasharray=\"10,6\"";
            case "dotted"   -> " stroke-dasharray=\"2,5\"";
            case "dashdot"  -> " stroke-dasharray=\"10,5,2,5\"";
            case "longdash" -> " stroke-dasharray=\"20,8\"";
            default -> "";
        };
    }

    public static void appendArrowHead(StringBuilder out,
                                       double baseX, double baseY,
                                       double[] points,
                                       String stroke, double strokeWidth) {
        int n = points.length;
        double x1 = baseX + points[n - 4];
        double y1 = baseY + points[n - 3];
        double x2 = baseX + points[n - 2];
        double y2 = baseY + points[n - 1];
        double angle = Math.atan2(y2 - y1, x2 - x1);
        double len = 14;
        double a1 = angle + Math.PI - 0.35;
        double a2 = angle + Math.PI + 0.35;
        out.append(String.format(Locale.ROOT,
                "<polyline points=\"%.2f,%.2f %.2f,%.2f %.2f,%.2f\" " +
                        "fill=\"none\" stroke=\"%s\" stroke-width=\"%.2f\" " +
                        "stroke-linecap=\"round\" stroke-linejoin=\"round\"/>\n",
                x2 + len * Math.cos(a1), y2 + len * Math.sin(a1),
                x2, y2,
                x2 + len * Math.cos(a2), y2 + len * Math.sin(a2),
                stroke, strokeWidth));
    }
}