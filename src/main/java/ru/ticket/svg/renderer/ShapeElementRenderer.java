package ru.ticket.svg.renderer;

import ru.ticket.dto.AppearanceFill;
import ru.ticket.dto.AppearanceStroke;
import ru.ticket.dto.GradientStop;
import ru.ticket.dto.Padding;
import ru.ticket.dto.ShapeElement;
import ru.ticket.svg.SvgContext;
import ru.ticket.util.XmlEscape;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class ShapeElementRenderer implements ElementRenderer<ShapeElement> {

    /** Дефолтное отношение внутреннего радиуса звезды к внешнему (как во фронте). */
    private static final double DEFAULT_STAR_INNER_RATIO = 1.0 / 2.4;

    @Override
    public Class<ShapeElement> supports() { return ShapeElement.class; }

    @Override
    public void render(ShapeElement s, SvgContext ctx) {
        StringBuilder out = ctx.body();

        boolean flipX = Boolean.TRUE.equals(s.flipX);
        boolean flipY = Boolean.TRUE.equals(s.flipY);

        double tx = s.x + (flipX ? s.width : 0);
        double ty = s.y + (flipY ? s.height : 0);

        String tr = String.format(Locale.ROOT,
                " transform=\"translate(%.2f,%.2f) rotate(%.2f) scale(%.2f, %.2f)\"",
                (double) tx,
                (double) ty,
                (double) s.rotation,
                flipX ? -1.0 : 1.0,
                flipY ? -1.0 : 1.0);

        out.append("<g").append(tr).append(">\n");

        if (s.appearance != null && s.appearance.fills != null && !s.appearance.fills.isEmpty()) {
            for (AppearanceFill fill : s.appearance.fills) {
                String fillAttr;
                if ("linear".equals(fill.type)) {
                    String id = ctx.defs().nextId("grad");
                    ctx.appendDefs(buildLinearGradient(id, fill, s));
                    fillAttr = "url(#" + id + ")";
                } else if ("radial".equals(fill.type)) {
                    String id = ctx.defs().nextId("grad");
                    ctx.appendDefs(buildRadialGradient(id, fill, s));
                    fillAttr = "url(#" + id + ")";
                } else {
                    fillAttr = fill.color != null ? fill.color : "none";
                }
                renderShapePrimitive(out, s, fillAttr, "none",
                        String.format(Locale.ROOT, "%.2f", (double) s.strokeWidth), "");
            }

            if (s.appearance.strokes != null && !s.appearance.strokes.isEmpty()) {
                for (AppearanceStroke stroke : s.appearance.strokes) {
                    String dash = dashAttr(stroke.dash);
                    renderShapePrimitive(out, s, "none", stroke.color,
                            String.format(Locale.ROOT, "%.2f", (double) stroke.width), dash);
                }
            }
        } else {
            String fillAttr = (s.fill == null || "transparent".equals(s.fill) || s.fill.isEmpty()) ? "none" : s.fill;
            String strokeAttr = s.stroke == null ? "none" : s.stroke;
            renderShapePrimitive(out, s, fillAttr, strokeAttr,
                    String.format(Locale.ROOT, "%.2f", (double) s.strokeWidth), "");
        }

        if (s.text != null && !s.text.isEmpty()) {
            Padding pad = s.textPadding != null ? s.textPadding : new Padding();
            double innerW = Math.max(1, s.width - pad.left - pad.right);
            double innerH = Math.max(1, s.height - pad.top - pad.bottom);

            String anchor = "center".equals(s.textAlign) ? "middle"
                    : "right".equals(s.textAlign)  ? "end"
                    : "start";

            double textX;
            if ("center".equals(s.textAlign)) {
                textX = pad.left + innerW / 2;
            } else if ("right".equals(s.textAlign)) {
                textX = pad.left + innerW;
            } else {
                textX = pad.left;
            }

            String verticalAlign = s.textVerticalAlign != null ? s.textVerticalAlign : "middle";
            double textY;
            if ("top".equals(verticalAlign)) {
                textY = pad.top + s.fontSize;
            } else if ("bottom".equals(verticalAlign)) {
                textY = s.height - pad.bottom;
            } else {
                textY = pad.top + innerH / 2 + s.fontSize * 0.35;
            }

            String fontFamily = s.textFontFamily != null ? s.textFontFamily : "Arial";

            out.append(String.format(Locale.ROOT,
                    "<text x=\"%.2f\" y=\"%.2f\" font-size=\"%.2f\" font-family=\"%s\" " +
                            "fill=\"%s\" text-anchor=\"%s\" xml:space=\"preserve\">%s</text>\n",
                    (double) textX,
                    (double) textY,
                    (double) s.fontSize,
                    XmlEscape.attr(fontFamily),
                    s.textFill,
                    anchor,
                    XmlEscape.escape(s.text)));
        }

        out.append("</g>\n");
    }

    private String buildLinearGradient(String id, AppearanceFill fill, ShapeElement s) {
        double angle = fill.angle != null ? fill.angle : 45.0;
        double rad = Math.toRadians(angle);
        double cx = s.width / 2.0;
        double cy = s.height / 2.0;
        double len = Math.hypot(s.width, s.height) / 2.0;
        double dx = Math.cos(rad) * len;
        double dy = Math.sin(rad) * len;
        double x1 = cx - dx, y1 = cy - dy;
        double x2 = cx + dx, y2 = cy + dy;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT,
                "<linearGradient id=\"%s\" gradientUnits=\"userSpaceOnUse\" " +
                        "x1=\"%.2f\" y1=\"%.2f\" x2=\"%.2f\" y2=\"%.2f\" spreadMethod=\"pad\">",
                id, (double) x1, (double) y1, (double) x2, (double) y2));
        appendStops(sb, fill);
        sb.append("</linearGradient>");
        return sb.toString();
    }

    private String buildRadialGradient(String id, AppearanceFill fill, ShapeElement s) {
        double cx = s.width / 2.0;
        double cy = s.height / 2.0;
        double r = Math.max(s.width, s.height) / 2.0;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT,
                "<radialGradient id=\"%s\" gradientUnits=\"userSpaceOnUse\" " +
                        "cx=\"%.2f\" cy=\"%.2f\" r=\"%.2f\" spreadMethod=\"pad\">",
                id, (double) cx, (double) cy, (double) r));
        appendStops(sb, fill);
        sb.append("</radialGradient>");
        return sb.toString();
    }

    private void appendStops(StringBuilder sb, AppearanceFill fill) {
        if (fill.stops == null || fill.stops.isEmpty()) {
            String c = fill.color != null ? fill.color : "#000000";
            sb.append(String.format(Locale.ROOT, "<stop offset=\"0\" stop-color=\"%s\"/>", c));
            sb.append(String.format(Locale.ROOT, "<stop offset=\"1\" stop-color=\"%s\"/>", c));
            return;
        }
        for (GradientStop stop : fill.stops) {
            double offset = Math.max(0.0, Math.min(1.0, stop.offset));
            sb.append(String.format(Locale.ROOT,
                    "<stop offset=\"%.4f\" stop-color=\"%s\"/>",
                    (double) offset, stop.color));
        }
    }

    private void renderShapePrimitive(StringBuilder out, ShapeElement s,
                                      String fill, String stroke, String sw, String extra) {
        String common = String.format(Locale.ROOT,
                "fill=\"%s\" stroke=\"%s\" stroke-width=\"%s\"%s", fill, stroke, sw, extra);

        switch (s.shape == null ? "rect" : s.shape) {
            case "rect", "roundRect" -> out.append(String.format(Locale.ROOT,
                    "<rect x=\"0\" y=\"0\" width=\"%.2f\" height=\"%.2f\" rx=\"%.2f\" %s/>\n",
                    (double) s.width, (double) s.height,
                    s.radius == null ? 0.0 : (double) s.radius, common));

            case "circle" -> out.append(String.format(Locale.ROOT,
                    "<ellipse cx=\"%.2f\" cy=\"%.2f\" rx=\"%.2f\" ry=\"%.2f\" %s/>\n",
                    (double) (s.width / 2), (double) (s.height / 2),
                    (double) (s.width / 2), (double) (s.height / 2), common));

            case "triangle" -> {
                // Пользователь мог перетащить вершины (polygonPoints).
                // Если они заданы — рисуем по точкам, как на канве.
                if (s.polygonPoints != null && s.polygonPoints.size() >= 6) {
                    String pts = formatPolygonPoints(s.polygonPoints);
                    if (!pts.isEmpty()) {
                        out.append(String.format("<polygon points=\"%s\" %s/>\n", pts, common));
                    }
                } else if (s.polygonSubpaths != null && !s.polygonSubpaths.isEmpty()) {
                    appendPolygonSubpaths(out, s, common);
                } else {
                    // Дефолтный треугольник: вершина сверху по центру.
                    out.append(String.format(Locale.ROOT,
                            "<polygon points=\"%.2f,%.2f %.2f,%.2f %.2f,%.2f\" %s/>\n",
                            (double) (s.width / 2), 0.0,
                            (double) s.width, (double) s.height,
                            0.0, (double) s.height, common));
                }
            }

            case "star" -> {
                // Тот же принцип: если вершины редактировались — по точкам.
                if (s.polygonPoints != null && s.polygonPoints.size() >= 6) {
                    String pts = formatPolygonPoints(s.polygonPoints);
                    if (!pts.isEmpty()) {
                        out.append(String.format("<polygon points=\"%s\" %s/>\n", pts, common));
                    }
                } else {
                    appendStar(out, s, fill, stroke, sw);
                }
            }

            case "arrow"  -> appendArrow(out, s, fill, stroke, sw);
            case "polygon" -> appendPolygon(out, s, common);
            case "arc"    -> appendArc(out, s, common);

            default -> out.append(String.format(Locale.ROOT,
                    "<rect x=\"0\" y=\"0\" width=\"%.2f\" height=\"%.2f\" %s/>\n",
                    (double) s.width, (double) s.height, common));
        }
    }

    /**
     * Форматирует плоский список точек [x0,y0,x1,y1,...] в SVG points-строку.
     * Защита от null и неполных пар — на случай undefined в исходном JSON.
     */
    private String formatPolygonPoints(List<Double> points) {
        if (points == null || points.size() < 6) return "";
        StringBuilder sb = new StringBuilder();
        int written = 0;
        for (int i = 0; i + 1 < points.size(); i += 2) {
            Double x = points.get(i);
            Double y = points.get(i + 1);
            if (x == null || y == null) continue;
            if (written > 0) sb.append(' ');
            sb.append(String.format(Locale.ROOT, "%.2f,%.2f", (double) x, (double) y));
            written++;
        }
        return written >= 3 ? sb.toString() : "";
    }

    private void appendPolygonSubpaths(StringBuilder out, ShapeElement s, String common) {
        StringBuilder d = new StringBuilder();
        for (List<Double> ring : s.polygonSubpaths) {
            if (ring == null || ring.size() < 6) continue;
            d.append("M ");
            boolean firstPoint = true;
            for (int i = 0; i + 1 < ring.size(); i += 2) {
                Double x = ring.get(i);
                Double y = ring.get(i + 1);
                if (x == null || y == null) continue;
                if (!firstPoint) d.append(" L ");
                d.append(String.format(Locale.ROOT, "%.2f,%.2f", (double) x, (double) y));
                firstPoint = false;
            }
            d.append(" Z ");
        }
        String path = d.toString().trim();
        if (!path.isEmpty()) {
            out.append(String.format("<path d=\"%s\" fill-rule=\"evenodd\" %s/>\n", path, common));
        }
    }

    private void appendPolygon(StringBuilder out, ShapeElement s, String common) {
        if (s.polygonSubpaths != null && !s.polygonSubpaths.isEmpty()) {
            appendPolygonSubpaths(out, s, common);
            return;
        }
        if (s.polygonPoints != null && s.polygonPoints.size() >= 6) {
            String pts = formatPolygonPoints(s.polygonPoints);
            if (!pts.isEmpty()) {
                out.append(String.format("<polygon points=\"%s\" %s/>\n", pts, common));
            }
        }
    }

    private void appendArc(StringBuilder out, ShapeElement s, String common) {
        double cx = s.width / 2;
        double cy = s.height / 2;
        double outerR = Math.min(s.width, s.height) / 2 * (s.arcOuterRadius != null ? s.arcOuterRadius : 1.0);
        double innerR = outerR * (s.arcInnerRadius != null ? s.arcInnerRadius : 0.5);
        double a0 = Math.toRadians(s.arcStartAngle != null ? s.arcStartAngle : 0);
        double a1 = Math.toRadians(s.arcEndAngle != null ? s.arcEndAngle : 90);

        double p1x = cx + Math.cos(a0) * outerR;
        double p1y = cy + Math.sin(a0) * outerR;
        double p2x = cx + Math.cos(a1) * outerR;
        double p2y = cy + Math.sin(a1) * outerR;
        double p3x = cx + Math.cos(a1) * innerR;
        double p3y = cy + Math.sin(a1) * innerR;
        double p4x = cx + Math.cos(a0) * innerR;
        double p4y = cy + Math.sin(a0) * innerR;

        int sweep = a1 > a0 ? 1 : 0;
        int large = Math.abs(a1 - a0) > Math.PI ? 1 : 0;

        String d;
        if (innerR <= 0.01) {
            d = String.format(Locale.ROOT,
                    "M %.2f %.2f A %.2f %.2f 0 %d %d %.2f %.2f L %.2f %.2f Z",
                    (double) p1x, (double) p1y, (double) outerR, (double) outerR, large, sweep,
                    (double) p2x, (double) p2y, (double) cx, (double) cy);
        } else {
            d = String.format(Locale.ROOT,
                    "M %.2f %.2f A %.2f %.2f 0 %d %d %.2f %.2f " +
                            "L %.2f %.2f A %.2f %.2f 0 %d %d %.2f %.2f Z",
                    (double) p1x, (double) p1y, (double) outerR, (double) outerR, large, sweep,
                    (double) p2x, (double) p2y,
                    (double) p3x, (double) p3y, (double) innerR, (double) innerR, large, 1 - sweep,
                    (double) p4x, (double) p4y);
        }
        out.append(String.format("<path d=\"%s\" fill-rule=\"evenodd\" %s/>\n", d, common));
    }

    /**
     * Звезда. Отношение внутреннего радиуса берётся из s.innerRadiusRatio
     * (пользователь меняет его, потянув вершину звезды на канве).
     * Без этого значения — дефолт 1/2.4, как в исходной фабрике фронта.
     */
    private void appendStar(StringBuilder out, ShapeElement s, String fill, String stroke, String sw) {
        double cx = s.width / 2;
        double cy = s.height / 2;
        double outer = Math.min(s.width, s.height) / 2;
        double ratio = (s.innerRadiusRatio != null && s.innerRadiusRatio > 0)
                ? s.innerRadiusRatio
                : DEFAULT_STAR_INNER_RATIO;
        ratio = Math.max(0.05, Math.min(0.99, ratio));
        double inner = outer * ratio;

        StringBuilder pts = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            double ang = -Math.PI / 2 + i * Math.PI / 5;
            double r = (i % 2 == 0) ? outer : inner;
            if (i > 0) pts.append(' ');
            pts.append(String.format(Locale.ROOT, "%.2f,%.2f",
                    (double) (cx + r * Math.cos(ang)),
                    (double) (cy + r * Math.sin(ang))));
        }
        out.append(String.format(
                "<polygon points=\"%s\" fill=\"%s\" stroke=\"%s\" stroke-width=\"%s\"/>\n",
                pts, fill, stroke, sw));
    }

    private void appendArrow(StringBuilder out, ShapeElement s, String fill, String stroke, String sw) {
        double h = s.height, w = s.width;
        String pts = String.format(Locale.ROOT,
                "%.2f,%.2f %.2f,%.2f %.2f,%.2f %.2f,%.2f %.2f,%.2f %.2f,%.2f",
                0.0, (double) (h / 2),
                (double) (w - 20), (double) (h / 2),
                (double) (w - 20), (double) (h / 2 - 15),
                (double) w, (double) (h / 2),
                (double) (w - 20), (double) (h / 2 + 15),
                (double) (w - 20), (double) (h / 2));
        out.append(String.format(
                "<polygon points=\"%s\" fill=\"%s\" stroke=\"%s\" stroke-width=\"%s\"/>\n",
                pts, fill, stroke, sw));
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
}