package ru.ticket.svg.renderer.stamp;

import ru.ticket.dto.StampElement;
import ru.ticket.svg.SvgContext;
import ru.ticket.util.XmlEscape;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Рендер legacy-штампа (frontend StampEl, ветка "LEGACY STAMP").
 *
 * Legacy-поля: shape, stampColor, strokeDash, showInner, top/center/bottom…
 * Формат совпадает с newLegacyStamp из lib/factories.ts.
 */
@Component
public class LegacyStampWriter {

    private static final double PAD = 8;

    public void write(StringBuilder out, StampElement s, SvgContext ctx) {
        String color = nz(s.stampColor, "#1a1a1a");
        double sw = s.strokeWidth != null && s.strokeWidth > 0 ? s.strokeWidth : 3;
        String dash = dashAttr(s.strokeDash);

        double w = s.width;
        double h = s.height;

        // 1. Внешний контур
        appendOutline(out, s.shape, 0, 0, w, h, color, sw, dash, null);

        // 2. Внутренний контур
        if (Boolean.TRUE.equals(s.showInner)) {
            double isw = s.innerStrokeWidth != null && s.innerStrokeWidth > 0
                    ? s.innerStrokeWidth : 1;
            String idash = dashAttr(s.innerStrokeDash);
            appendOutline(out, s.shape, PAD, PAD, w - PAD * 2, h - PAD * 2,
                    color, isw, idash, null);
        }

        // 3. Верхний текст
        if (notBlank(s.topText)) {
            String fs = nz(s.topFontFamily, "Arial");
            String style = nz(s.topFontStyle, "bold");
            String tc = nz(s.topColor, color);
            double size = s.topFontSize != null && s.topFontSize > 0 ? s.topFontSize : 14;

            if (isRound(s.shape)) {
                writeArcText(out, ctx, s.topText, w / 2, h / 2,
                        Math.min(w, h) / 2 - 15,
                        -150, -30, size, fs, style, tc);
            } else {
                writeStraightText(out, 0, 10, w, 0, s.topText, size, fs, style, tc);
            }
        }

        // 4. Нижний текст
        if (notBlank(s.bottomText)) {
            String fs = nz(s.bottomFontFamily, "Arial");
            String style = nz(s.bottomFontStyle, "normal");
            String tc = nz(s.bottomColor, color);
            double size = s.bottomFontSize != null && s.bottomFontSize > 0 ? s.bottomFontSize : 12;

            if (isRound(s.shape)) {
                writeArcText(out, ctx, s.bottomText, w / 2, h / 2,
                        Math.min(w, h) / 2 - 15,
                        150, 30, size, fs, style, tc);
            } else {
                double ty = h - size - 10;
                writeStraightText(out, 0, ty, w, 0, s.bottomText, size, fs, style, tc);
            }
        }

        // 5. Центральный текст
        if (notBlank(s.centerText)) {
            String fs = nz(s.centerFontFamily, "Arial");
            String style = nz(s.centerFontStyle, "bold");
            String tc = nz(s.centerColor, color);
            double size = s.centerFontSize != null && s.centerFontSize > 0 ? s.centerFontSize : 20;
            double ty = h / 2 - size / 2;
            writeStraightText(out, 0, ty, w, 0, s.centerText, size, fs, style, tc);
        }
    }

    private void appendOutline(StringBuilder out, String shape,
                               double x, double y, double w, double h,
                               String stroke, double sw, String dash,
                               @SuppressWarnings("unused") Object ignore) {
        String sh = shape == null ? "circle" : shape;
        switch (sh) {
            case "circle", "oval" -> out.append(String.format(Locale.ROOT,
                    "<ellipse cx=\"%.2f\" cy=\"%.2f\" rx=\"%.2f\" ry=\"%.2f\" " +
                            "fill=\"none\" stroke=\"%s\" stroke-width=\"%.2f\"%s/>\n",
                    x + w / 2, y + h / 2, w / 2, h / 2,
                    stroke, sw, dash));
            case "rect" -> out.append(String.format(Locale.ROOT,
                    "<rect x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\" rx=\"12\" " +
                            "fill=\"none\" stroke=\"%s\" stroke-width=\"%.2f\"%s/>\n",
                    x, y, w, h, stroke, sw, dash));
            case "triangle" -> out.append(String.format(Locale.ROOT,
                    "<polygon points=\"%.2f,%.2f %.2f,%.2f %.2f,%.2f\" " +
                            "fill=\"none\" stroke=\"%s\" stroke-width=\"%.2f\"%s/>\n",
                    x + w / 2, y,
                    x + w,     y + h,
                    x,         y + h,
                    stroke, sw, dash));
            default -> out.append(String.format(Locale.ROOT,
                    "<ellipse cx=\"%.2f\" cy=\"%.2f\" rx=\"%.2f\" ry=\"%.2f\" " +
                            "fill=\"none\" stroke=\"%s\" stroke-width=\"%.2f\"%s/>\n",
                    x + w / 2, y + h / 2, w / 2, h / 2, stroke, sw, dash));
        }
    }

    private void writeStraightText(StringBuilder out,
                                   double x, double y, double w,
                                   @SuppressWarnings("unused") double ignored,
                                   String text, double size,
                                   String family, String style, String color) {
        String fontWeight = style != null && style.contains("bold") ? "700" : "400";
        out.append(String.format(Locale.ROOT,
                "<text x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" " +
                        "font-size=\"%.2f\" font-family=\"%s\" font-weight=\"%s\" " +
                        "fill=\"%s\" text-anchor=\"middle\">%s</text>\n",
                x + w / 2, y + size,
                w, size, XmlEscape.attr(family), fontWeight,
                color, XmlEscape.escape(text)));
    }

    private void writeArcText(StringBuilder out, SvgContext ctx,
                              String text, double cx, double cy, double radius,
                              double startDeg, double endDeg,
                              double size, String family, String style, String color) {
        String pathId = ctx.defs().nextId("legacy-arc");

        double sa = Math.toRadians(startDeg);
        double ea = Math.toRadians(endDeg);
        double sx = cx + radius * Math.cos(sa);
        double sy = cy + radius * Math.sin(sa);
        double ex = cx + radius * Math.cos(ea);
        double ey = cy + radius * Math.sin(ea);
        int large = Math.abs(endDeg - startDeg) > 180 ? 1 : 0;
        int sweep = (endDeg > startDeg) ? 1 : 0;

        String d = String.format(Locale.ROOT,
                "M %.2f %.2f A %.2f %.2f 0 %d %d %.2f %.2f",
                sx, sy, radius, radius, large, sweep, ex, ey);
        ctx.appendDefs(String.format(Locale.ROOT,
                "<path id=\"%s\" d=\"%s\" fill=\"none\"/>", pathId, d));

        String weight = style != null && style.contains("bold") ? "700" : "400";
        String isItalic = style != null && style.contains("italic") ? "italic" : "normal";

        out.append(String.format(Locale.ROOT,
                "<text font-family=\"%s\" font-size=\"%.2f\" font-weight=\"%s\" " +
                        "font-style=\"%s\" fill=\"%s\" xml:space=\"preserve\">\n" +
                        "  <textPath href=\"#%s\" xlink:href=\"#%s\" startOffset=\"50%%\" " +
                        "text-anchor=\"middle\">%s</textPath>\n" +
                        "</text>\n",
                XmlEscape.attr(family), size, weight, isItalic,
                color, pathId, pathId, XmlEscape.escape(text)));
    }

    private boolean isRound(String shape) {
        return "circle".equals(shape) || "oval".equals(shape);
    }

    private String dashAttr(String dash) {
        if (dash == null) return "";
        return switch (dash) {
            case "dashed"   -> " stroke-dasharray=\"10,6\"";
            case "dotted"   -> " stroke-dasharray=\"2,5\"";
            case "dashdot"  -> " stroke-dasharray=\"10,5,2,5\"";
            case "longdash" -> " stroke-dasharray=\"20,8\"";
            default -> "";
        };
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
    private static String nz(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }
}