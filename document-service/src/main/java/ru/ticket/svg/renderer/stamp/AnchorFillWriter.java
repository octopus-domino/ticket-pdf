package ru.ticket.svg.renderer.stamp;

import ru.ticket.dto.StampShapeItem;
import ru.ticket.svg.SvgContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Пишет anchor-заливку штампа.
 *
 * Ключевое отличие от старой версии: <rect> заливки теперь стоит
 * в реальных координатах target-фигуры, а не в (0,0). Раньше
 * clipPath (userSpaceOnUse, в координатах target) и <rect> (0,0)
 * не совпадали → в PDF заливка оказывалась обрезанной со сдвигом.
 *
 * Структура вывода:
 *   <clipPath>  по target   — ограничивает прямоугольник формой
 *   <mask>      белое поле минус чёрные дырки
 *   <rect>      сам залитый прямоугольник, ровно по bbox target
 *
 * Дырки: если внутри target есть другие фигуры (например, внутренний
 * круг у печати), они вычитаются из заливки. Фигура, содержащая
 * точку anchor, дыркой не считается — это часть заливаемой области.
 */
@Component
public class AnchorFillWriter {

    /** Небольшой запас mask'а, чтобы rect не цеплялся за край. */
    private static final double MASK_PAD = 1.0;

    public void write(StringBuilder out,
                      StampShapeItem target,
                      String color,
                      SvgContext ctx,
                      List<StampShapeItem> allShapes,
                      double anchorX,
                      double anchorY) {

        List<StampShapeItem> holes = findHoles(target, allShapes, anchorX, anchorY);

        String clipId = ctx.defs().nextId("clip");
        String maskId = ctx.defs().nextId("mask");

        /* clipPath — по target, в его собственных координатах. */
        ctx.appendDefs(String.format(Locale.ROOT,
                "<clipPath id=\"%s\" clipPathUnits=\"userSpaceOnUse\">" +
                        "<path d=\"%s\"/></clipPath>",
                clipId, shapeToPathData(target)));

        /* mask — тот же user space, что и у rect.
         * Область mask'а должна покрывать bbox target'а, иначе
         * сам mask дополнительно обрежет фигуру. */
        double maskX = target.x - MASK_PAD;
        double maskY = target.y - MASK_PAD;
        double maskW = target.width + MASK_PAD * 2;
        double maskH = target.height + MASK_PAD * 2;

        StringBuilder mask = new StringBuilder();
        mask.append(String.format(Locale.ROOT,
                "<mask id=\"%s\" maskUnits=\"userSpaceOnUse\" " +
                        "x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\">",
                maskId, maskX, maskY, maskW, maskH));
        mask.append(String.format(Locale.ROOT,
                "<rect x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\" fill=\"white\"/>",
                maskX, maskY, maskW, maskH));
        for (StampShapeItem hole : holes) {
            mask.append(String.format(Locale.ROOT,
                    "<path d=\"%s\" fill=\"black\"/>",
                    shapeToPathData(hole)));
        }
        mask.append("</mask>");
        ctx.appendDefs(mask.toString());

        /* Сам rect заливки — по bbox target'а, а не в (0,0). */
        out.append(String.format(Locale.ROOT,
                "<rect x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\" " +
                        "fill=\"%s\" clip-path=\"url(#%s)\" mask=\"url(#%s)\"/>\n",
                target.x, target.y, target.width, target.height,
                color == null ? "#fbbf24" : color,
                clipId, maskId));
    }

    /* ============================================================
       Поиск дырок
       ============================================================
       Дырка — это фигура, которая:
        1. не является target,
        2. не line и не fill,
        3. не скрыта,
        4. имеет видимую обводку (иначе она не «стенка»),
        5. полностью вписана в target,
        6. не содержит точку якоря (иначе это не дырка, а часть
           заливаемой области).
    */

    private List<StampShapeItem> findHoles(StampShapeItem target,
                                           List<StampShapeItem> allShapes,
                                           double anchorX,
                                           double anchorY) {
        List<StampShapeItem> holes = new ArrayList<>();

        for (StampShapeItem s : allShapes) {
            if (s.id == null || s.id.equals(target.id)) continue;
            if ("line".equals(s.kind)) continue;
            if ("fill".equals(s.kind)) continue;
            if (Boolean.TRUE.equals(s.hidden)) continue;

            if (s.strokeWidth <= 0) continue;
            if (s.stroke == null || s.stroke.isEmpty()) continue;

            if (!isInside(s, target)) continue;
            if (containsPoint(s, anchorX, anchorY)) continue;

            holes.add(s);
        }
        return holes;
    }

    private boolean isInside(StampShapeItem inner, StampShapeItem outer) {
        return inner.x >= outer.x - 0.5
                && inner.y >= outer.y - 0.5
                && inner.x + inner.width <= outer.x + outer.width + 0.5
                && inner.y + inner.height <= outer.y + outer.height + 0.5;
    }

    private boolean containsPoint(StampShapeItem s, double px, double py) {
        if (px < s.x || px > s.x + s.width) return false;
        if (py < s.y || py > s.y + s.height) return false;

        switch (s.kind) {
            case "circle", "ellipse" -> {
                double cx = s.x + s.width / 2;
                double cy = s.y + s.height / 2;
                double rx = Math.max(0.001, s.width / 2);
                double ry = Math.max(0.001, s.height / 2);
                double dx = (px - cx) / rx;
                double dy = (py - cy) / ry;
                return dx * dx + dy * dy <= 1;
            }
            case "triangle" -> {
                return pointInTriangle(
                        px - s.x, py - s.y,
                        s.width / 2, 0,
                        s.width, s.height,
                        0, s.height);
            }
            default -> {
                return true;
            }
        }
    }

    private boolean pointInTriangle(double px, double py,
                                    double ax, double ay,
                                    double bx, double by,
                                    double cx, double cy) {
        double d1 = sign(px, py, ax, ay, bx, by);
        double d2 = sign(px, py, bx, by, cx, cy);
        double d3 = sign(px, py, cx, cy, ax, ay);
        boolean hasNeg = d1 < 0 || d2 < 0 || d3 < 0;
        boolean hasPos = d1 > 0 || d2 > 0 || d3 > 0;
        return !(hasNeg && hasPos);
    }

    private double sign(double px, double py,
                        double ax, double ay,
                        double bx, double by) {
        return (px - bx) * (ay - by) - (ax - bx) * (py - by);
    }

    /* ============================================================
       Конвертация фигуры в SVG path
       ============================================================ */

    private String shapeToPathData(StampShapeItem s) {
        double x = s.x, y = s.y, w = s.width, h = s.height;
        return switch (s.kind) {
            case "rect" -> {
                double r = Math.max(0, Math.min(Math.min(s.cornerRadius, w / 2), h / 2));
                if (r <= 0) {
                    yield String.format(Locale.ROOT,
                            "M %.2f %.2f H %.2f V %.2f H %.2f Z", x, y, x + w, y + h, x);
                }
                yield String.format(Locale.ROOT,
                        "M %.2f %.2f H %.2f A %.2f %.2f 0 0 1 %.2f %.2f " +
                                "V %.2f A %.2f %.2f 0 0 1 %.2f %.2f H %.2f " +
                                "A %.2f %.2f 0 0 1 %.2f %.2f V %.2f A %.2f %.2f 0 0 1 %.2f %.2f Z",
                        x + r, y, x + w - r,
                        r, r, x + w, y + r, y + h - r,
                        r, r, x + w - r, y + h, x + r,
                        r, r, x, y + h - r, y + r,
                        r, r, x + r, y);
            }
            case "circle", "ellipse" -> {
                double cx = x + w / 2, cy = y + h / 2;
                double rx = w / 2, ry = h / 2;
                yield String.format(Locale.ROOT,
                        "M %.2f %.2f A %.2f %.2f 0 1 0 %.2f %.2f A %.2f %.2f 0 1 0 %.2f %.2f Z",
                        cx - rx, cy, rx, ry, cx + rx, cy, rx, ry, cx - rx, cy);
            }
            case "triangle" -> String.format(Locale.ROOT,
                    "M %.2f %.2f L %.2f %.2f L %.2f %.2f Z",
                    x + w / 2, y, x + w, y + h, x, y + h);
            default -> String.format(Locale.ROOT,
                    "M %.2f %.2f H %.2f V %.2f H %.2f Z", x, y, x + w, y + h, x);
        };
    }
}