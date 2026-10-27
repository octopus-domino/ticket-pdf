package ru.ticket.svg.renderer.stamp;

import ru.ticket.dto.StampShapeItem;
import ru.ticket.svg.SvgSerializationUtil;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class StampShapeWriter {

    public void write(StringBuilder out, StampShapeItem s) {
        String fillAttr = s.fill == null ? "none" : s.fill;
        String strokeAttr = s.stroke == null ? "none" : s.stroke;
        String dashArr = SvgSerializationUtil.dashAttr(s.dash);

        String common = String.format(Locale.ROOT,
                "fill=\"%s\" stroke=\"%s\" stroke-width=\"%.2f\"%s",
                fillAttr, strokeAttr, s.strokeWidth, dashArr);

        String tr = (s.rotation != null && s.rotation != 0)
                ? String.format(Locale.ROOT, " transform=\"rotate(%.2f %.2f %.2f)\"",
                s.rotation, s.x + s.width / 2, s.y + s.height / 2)
                : "";

        switch (s.kind) {
            case "rect" -> out.append(String.format(Locale.ROOT,
                    "<rect x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\" rx=\"%.2f\" %s%s/>\n",
                    s.x, s.y, s.width, s.height, s.cornerRadius, common, tr));

            case "circle", "ellipse" -> out.append(String.format(Locale.ROOT,
                    "<ellipse cx=\"%.2f\" cy=\"%.2f\" rx=\"%.2f\" ry=\"%.2f\" %s%s/>\n",
                    s.x + s.width / 2, s.y + s.height / 2,
                    s.width / 2, s.height / 2, common, tr));

            case "triangle" -> out.append(String.format(Locale.ROOT,
                    "<polygon points=\"%.2f,%.2f %.2f,%.2f %.2f,%.2f\" %s%s/>\n",
                    s.x + s.width / 2, s.y,
                    s.x + s.width,   s.y + s.height,
                    s.x,             s.y + s.height, common, tr));

            case "line" -> writeLine(out, s, strokeAttr, dashArr, tr);
        }
    }

    private void writeLine(StringBuilder out, StampShapeItem s,
                           String strokeAttr, String dashArr, String tr) {
        double[] pts = s.points != null && s.points.length >= 4
                ? s.points
                : new double[]{0, s.height / 2, s.width, s.height / 2};

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < pts.length; i += 2) {
            if (i > 0) sb.append(' ');
            sb.append(String.format(Locale.ROOT, "%.2f,%.2f",
                    s.x + pts[i], s.y + pts[i + 1]));
        }

        out.append(String.format(Locale.ROOT,
                "<polyline points=\"%s\" fill=\"none\" stroke=\"%s\" stroke-width=\"%.2f\"%s " +
                        "stroke-linecap=\"round\" stroke-linejoin=\"round\"%s/>\n",
                sb, strokeAttr, s.strokeWidth, dashArr, tr));
    }
}