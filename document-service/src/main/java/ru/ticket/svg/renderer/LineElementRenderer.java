package ru.ticket.svg.renderer;

import ru.ticket.dto.LineElement;
import ru.ticket.svg.SvgContext;
import ru.ticket.svg.SvgSerializationUtil;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class LineElementRenderer implements ElementRenderer<LineElement> {

    @Override
    public Class<LineElement> supports() { return LineElement.class; }

    @Override
    public void render(LineElement l, SvgContext ctx) {
        if (l.points == null || l.points.length < 4) return;

        StringBuilder out = ctx.body();

        String tr = l.rotation != 0
                ? String.format(Locale.ROOT, " transform=\"rotate(%.2f %.2f %.2f)\"",
                l.rotation, l.x, l.y)
                : "";

        out.append("<g").append(tr).append(">\n");

        StringBuilder pts = new StringBuilder();
        for (int i = 0; i + 1 < l.points.length; i += 2) {
            if (i > 0) pts.append(' ');
            pts.append(String.format(Locale.ROOT, "%.2f,%.2f",
                    l.x + l.points[i], l.y + l.points[i + 1]));
        }

        String dashArr = SvgSerializationUtil.dashAttr(l.dash);

        out.append(String.format(Locale.ROOT,
                "<polyline points=\"%s\" fill=\"none\" stroke=\"%s\" stroke-width=\"%.2f\"%s " +
                        "stroke-linecap=\"round\" stroke-linejoin=\"round\"/>\n",
                pts, l.stroke, l.strokeWidth, dashArr));

        if (l.isArrow && l.points.length >= 4) {
            SvgSerializationUtil.appendArrowHead(out, l.x, l.y, l.points, l.stroke, l.strokeWidth);
        }

        out.append("</g>\n");
    }
}