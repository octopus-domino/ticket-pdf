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

    @Override
    public Class<ShapeElement> supports() { return ShapeElement.class; }

    @Override
    public void render(ShapeElement s, SvgContext ctx) {
        StringBuilder out = ctx.body();

        String tr = s.rotation != 0
                ? String.format(Locale.ROOT, " transform=\"rotate(%.2f %.2f %.2f)\"",
                s.rotation, s.x, s.y)
                : "";
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
                        String.format(Locale.ROOT, "%.2f", s.strokeWidth), "");
            }

            if (s.appearance.strokes != null && !s.appearance.strokes.isEmpty()) {
                for (AppearanceStroke stroke : s.appearance.strokes) {
                    String dash = dashAttr(stroke.dash);
                    renderShapePrimitive(out, s, "none", stroke.color,
                            String.format(Locale.ROOT, "%.2f", stroke.width), dash);
                }
            }
        } else {
            String fillAttr = "transparent".equals(s.fill) ? "none" : s.fill;
            String strokeAttr = s.stroke == null ? "none" : s.stroke;
            renderShapePrimitive(out, s, fillAttr, strokeAttr,
                    String.format(Locale.ROOT, "%.2f", s.strokeWidth), "");
        }

        if (s.text != null && !s.text.isEmpty()) {
            Padding pad = s.textPadding != null ? s.textPadding : new Padding();
            double innerW = Math.max(1, s.width - pad.left - pad.right);
            double innerH = Math.max(1, s.height - pad.top - pad.bottom);

            String anchor = "center".equals(s.textAlign) ? "middle"
                    : "right".equals(s.textAlign)  ? "end"
                    : "start";

            double tx;
            if ("center".equals(s.textAlign)) {
                tx = s.x + pad.left + innerW / 2;
            } else if ("right".equals(s.textAlign)) {
                tx = s.x + pad.left + innerW;
            } else {
                tx = s.x + pad.left;
            }

            String verticalAlign = s.textVerticalAlign != null ? s.textVerticalAlign : "middle";
            double ty;
            if ("top".equals(verticalAlign)) {
                ty = s.y + pad.top + s.fontSize;
            } else if ("bottom".equals(verticalAlign)) {
                ty = s.y + s.height - pad.bottom;
            } else {
                ty = s.y + pad.top + innerH / 2 + s.fontSize * 0.35;
            }

            String fontFamily = s.textFontFamily != null ? s.textFontFamily : "Arial";

            out.append(String.format(Locale.ROOT,
                    "<text x=\"%.2f\" y=\"%.2f\" font-size=\"%.2f\" font-family=\"%s\" " +
                            "fill=\"%s\" text-anchor=\"%s\" xml:space=\"preserve\">%s</text>\n",
                    tx, ty, s.fontSize, XmlEscape.attr(fontFamily),
                    s.textFill, anchor, XmlEscape.escape(s.text)));
        }

        out.append("</g>\n");
    }

    private String buildLinearGradient(String id, AppearanceFill fill, ShapeElement s) {
        double angle = fill.angle != null ? fill.angle : 45.0;
        double rad = Math.toRadians(angle);
        double cx = s.x + s.width / 2.0;
        double cy = s.y + s.height / 2.0;
        double len = Math.hypot(s.width, s.height) / 2.0;
        double dx = Math.cos(rad) * len;
        double dy = Math.sin(rad) * len;
        double x1 = cx - dx, y1 = cy - dy;
        double x2 = cx + dx, y2 = cy + dy;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT,
                "<linearGradient id=\"%s\" gradientUnits=\"userSpaceOnUse\" " +
                        "x1=\"%.2f\" y1=\"%.2f\" x2=\"%.2f\" y2=\"%.2f\" spreadMethod=\"pad\">",
                id, x1, y1, x2, y2));
        appendStops(sb, fill);
        sb.append("</linearGradient>");
        return sb.toString();
    }

    private String buildRadialGradient(String id, AppearanceFill fill, ShapeElement s) {
        double cx = s.x + s.width / 2.0;
        double cy = s.y + s.height / 2.0;
        double r = Math.max(s.width, s.height) / 2.0;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT,
                "<radialGradient id=\"%s\" gradientUnits=\"userSpaceOnUse\" " +
                        "cx=\"%.2f\" cy=\"%.2f\" r=\"%.2f\" spreadMethod=\"pad\">",
                id, cx, cy, r));
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
                    offset, stop.color));
        }
    }

    private void renderShapePrimitive(StringBuilder out, ShapeElement s,
                                      String fill, String stroke, String sw, String extra) {
        String common = String.format(Locale.ROOT,
                "fill=\"%s\" stroke=\"%s\" stroke-width=\"%s\"%s", fill, stroke, sw, extra);

        switch (s.shape == null ? "rect" : s.shape) {
            case "rect", "roundRect" -> out.append(String.format(Locale.ROOT,
                    "<rect x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\" rx=\"%.2f\" %s/>\n",
                    s.x, s.y, s.width, s.height,
                    s.radius == null ? 0 : s.radius, common));

            case "circle" -> out.append(String.format(Locale.ROOT,
                    "<ellipse cx=\"%.2f\" cy=\"%.2f\" rx=\"%.2f\" ry=\"%.2f\" %s/>\n",
                    s.x + s.width / 2, s.y + s.height / 2,
                    s.width / 2, s.height / 2, common));

            case "triangle" -> out.append(String.format(Locale.ROOT,
                    "<polygon points=\"%.2f,%.2f %.2f,%.2f %.2f,%.2f\" %s/>\n",
                    s.x + s.width / 2, s.y,
                    s.x + s.width,     s.y + s.height,
                    s.x,               s.y + s.height, common));

            case "star"   -> appendStar(out, s, fill, stroke, sw);
            case "arrow"  -> appendArrow(out, s, fill, stroke, sw);
            case "polygon" -> appendPolygon(out, s, common);
            case "arc"    -> appendArc(out, s, common);

            default -> out.append(String.format(Locale.ROOT,
                    "<rect x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\" %s/>\n",
                    s.x, s.y, s.width, s.height, common));
        }
    }

    private void appendPolygon(StringBuilder out, ShapeElement s, String common) {
        if (s.polygonSubpaths != null && !s.polygonSubpaths.isEmpty()) {
            StringBuilder d = new StringBuilder();
            for (List<Double> ring : s.polygonSubpaths) {
                if (ring.size() < 6) continue;
                d.append("M ");
                for (int i = 0; i < ring.size(); i += 2) {
                    if (i > 0) d.append(" L ");
                    d.append(String.format(Locale.ROOT, "%.2f,%.2f",
                            s.x + ring.get(i), s.y + ring.get(i + 1)));
                }
                d.append(" Z ");
            }
            out.append(String.format("<path d=\"%s\" fill-rule=\"evenodd\" %s/>\n",
                    d.toString().trim(), common));
        } else if (s.polygonPoints != null && s.polygonPoints.size() >= 6) {
            StringBuilder pts = new StringBuilder();
            for (int i = 0; i < s.polygonPoints.size(); i += 2) {
                if (i > 0) pts.append(" ");
                pts.append(String.format(Locale.ROOT, "%.2f,%.2f",
                        s.x + s.polygonPoints.get(i),
                        s.y + s.polygonPoints.get(i + 1)));
            }
            out.append(String.format("<polygon points=\"%s\" %s/>\n", pts, common));
        }
    }

    private void appendArc(StringBuilder out, ShapeElement s, String common) {
        double cx = s.x + s.width / 2;
        double cy = s.y + s.height / 2;
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
                    p1x, p1y, outerR, outerR, large, sweep, p2x, p2y, cx, cy);
        } else {
            d = String.format(Locale.ROOT,
                    "M %.2f %.2f A %.2f %.2f 0 %d %d %.2f %.2f " +
                            "L %.2f %.2f A %.2f %.2f 0 %d %d %.2f %.2f Z",
                    p1x, p1y, outerR, outerR, large, sweep, p2x, p2y,
                    p3x, p3y, innerR, innerR, large, 1 - sweep, p4x, p4y);
        }
        out.append(String.format("<path d=\"%s\" fill-rule=\"evenodd\" %s/>\n", d, common));
    }

    private void appendStar(StringBuilder out, ShapeElement s, String fill, String stroke, String sw) {
        double cx = s.x + s.width / 2;
        double cy = s.y + s.height / 2;
        double outer = Math.min(s.width, s.height) / 2;
        double inner = outer / 2;
        StringBuilder pts = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            double ang = -Math.PI / 2 + i * Math.PI / 5;
            double r = (i % 2 == 0) ? outer : inner;
            if (i > 0) pts.append(' ');
            pts.append(String.format(Locale.ROOT, "%.2f,%.2f",
                    cx + r * Math.cos(ang), cy + r * Math.sin(ang)));
        }
        out.append(String.format(
                "<polygon points=\"%s\" fill=\"%s\" stroke=\"%s\" stroke-width=\"%s\"/>\n",
                pts, fill, stroke, sw));
    }

    private void appendArrow(StringBuilder out, ShapeElement s, String fill, String stroke, String sw) {
        double h = s.height, w = s.width;
        String pts = String.format(Locale.ROOT,
                "%.2f,%.2f %.2f,%.2f %.2f,%.2f %.2f,%.2f %.2f,%.2f %.2f,%.2f",
                s.x, s.y + h / 2,
                s.x + w - 20, s.y + h / 2,
                s.x + w - 20, s.y + h / 2 - 15,
                s.x + w, s.y + h / 2,
                s.x + w - 20, s.y + h / 2 + 15,
                s.x + w - 20, s.y + h / 2);
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