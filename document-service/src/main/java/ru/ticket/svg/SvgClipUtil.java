package ru.ticket.svg;

import ru.ticket.dto.ShapeElement;

import java.util.List;
import java.util.Locale;

/**
 * Генерирует SVG path 'd' из фигуры (для <path> и <clipPath>).
 * Тот же набор фигур, что в ShapeElementRenderer.
 * Если фигура не поддерживается — возвращает null.
 */
public final class SvgClipUtil {

    private SvgClipUtil() {}

    public static String shapeToPath(ShapeElement s) {
        if (s == null) return null;
        String shape = s.shape == null ? "rect" : s.shape;

        return switch (shape) {
            case "rect", "roundRect" -> {
                double r = s.radius != null ? s.radius : 0;
                if (r <= 0) {
                    yield String.format(Locale.ROOT,
                            "M %.2f %.2f H %.2f V %.2f H %.2f Z",
                            s.x, s.y, s.x + s.width, s.y + s.height, s.x);
                }
                yield roundRectPath(s.x, s.y, s.width, s.height, r);
            }
            case "circle" -> circlePath(s.x, s.y, s.width, s.height);
            case "triangle" -> String.format(Locale.ROOT,
                    "M %.2f %.2f L %.2f %.2f L %.2f %.2f Z",
                    s.x + s.width / 2, s.y,
                    s.x + s.width,     s.y + s.height,
                    s.x,               s.y + s.height);
            case "star" -> starPath(s.x, s.y, s.width, s.height);
            case "polygon" -> polygonPath(s);
            case "arc" -> arcPath(s);
            default -> null;
        };
    }

    private static String roundRectPath(double x, double y, double w, double h, double r) {
        double rr = Math.max(0, Math.min(r, Math.min(w / 2, h / 2)));
        if (rr <= 0) {
            return String.format(Locale.ROOT,
                    "M %.2f %.2f H %.2f V %.2f H %.2f Z", x, y, x + w, y + h, x);
        }
        return String.format(Locale.ROOT,
                "M %.2f %.2f H %.2f A %.2f %.2f 0 0 1 %.2f %.2f " +
                        "V %.2f A %.2f %.2f 0 0 1 %.2f %.2f H %.2f " +
                        "A %.2f %.2f 0 0 1 %.2f %.2f V %.2f A %.2f %.2f 0 0 1 %.2f %.2f Z",
                x + rr, y, x + w - rr,
                rr, rr, x + w, y + rr, y + h - rr,
                rr, rr, x + w - rr, y + h, x + rr,
                rr, rr, x, y + h - rr, y + rr,
                rr, rr, x + rr, y);
    }

    private static String circlePath(double x, double y, double w, double h) {
        double cx = x + w / 2;
        double cy = y + h / 2;
        double rx = w / 2;
        double ry = h / 2;
        return String.format(Locale.ROOT,
                "M %.2f %.2f A %.2f %.2f 0 1 0 %.2f %.2f A %.2f %.2f 0 1 0 %.2f %.2f Z",
                cx - rx, cy, rx, ry, cx + rx, cy, rx, ry, cx - rx, cy);
    }

    private static String starPath(double x, double y, double w, double h) {
        double cx = x + w / 2;
        double cy = y + h / 2;
        double outer = Math.min(w, h) / 2;
        double inner = outer / 2;
        StringBuilder d = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            double ang = -Math.PI / 2 + i * Math.PI / 5;
            double r = (i % 2 == 0) ? outer : inner;
            double px = cx + r * Math.cos(ang);
            double py = cy + r * Math.sin(ang);
            d.append(i == 0 ? "M " : " L ");
            d.append(String.format(Locale.ROOT, "%.2f %.2f", px, py));
        }
        d.append(" Z");
        return d.toString();
    }

    private static String polygonPath(ShapeElement s) {
        if (s.polygonSubpaths != null && !s.polygonSubpaths.isEmpty()) {
            StringBuilder d = new StringBuilder();
            for (List<Double> ring : s.polygonSubpaths) {
                if (ring.size() < 6) continue;
                d.append("M ");
                for (int i = 0; i < ring.size(); i += 2) {
                    if (i > 0) d.append(" L ");
                    d.append(String.format(Locale.ROOT, "%.2f %.2f",
                            s.x + ring.get(i), s.y + ring.get(i + 1)));
                }
                d.append(" Z ");
            }
            return d.toString().trim();
        }
        if (s.polygonPoints != null && s.polygonPoints.size() >= 6) {
            StringBuilder d = new StringBuilder();
            for (int i = 0; i < s.polygonPoints.size(); i += 2) {
                d.append(i == 0 ? "M " : " L ");
                d.append(String.format(Locale.ROOT, "%.2f %.2f",
                        s.x + s.polygonPoints.get(i),
                        s.y + s.polygonPoints.get(i + 1)));
            }
            d.append(" Z");
            return d.toString();
        }
        return null;
    }

    private static String arcPath(ShapeElement s) {
        double cx = s.x + s.width / 2;
        double cy = s.y + s.height / 2;
        double outerR = Math.min(s.width, s.height) / 2
                * (s.arcOuterRadius != null ? s.arcOuterRadius : 1.0);
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

        if (innerR <= 0.01) {
            return String.format(Locale.ROOT,
                    "M %.2f %.2f A %.2f %.2f 0 %d %d %.2f %.2f L %.2f %.2f Z",
                    p1x, p1y, outerR, outerR, large, sweep, p2x, p2y, cx, cy);
        }
        return String.format(Locale.ROOT,
                "M %.2f %.2f A %.2f %.2f 0 %d %d %.2f %.2f " +
                        "L %.2f %.2f A %.2f %.2f 0 %d %d %.2f %.2f Z",
                p1x, p1y, outerR, outerR, large, sweep, p2x, p2y,
                p3x, p3y, innerR, innerR, large, 1 - sweep, p4x, p4y);
    }
}