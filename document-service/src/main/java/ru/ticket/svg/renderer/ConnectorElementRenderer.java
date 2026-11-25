package ru.ticket.svg.renderer;

import ru.ticket.dto.ConnectorElement;
import ru.ticket.dto.Element;
import ru.ticket.svg.SvgContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class ConnectorElementRenderer implements ElementRenderer<ConnectorElement> {

    @Override
    public Class<ConnectorElement> supports() { return ConnectorElement.class; }

    @Override
    public void render(ConnectorElement c, SvgContext ctx) {
        Element from = findElement(ctx, c.fromId);
        Element to = findElement(ctx, c.toId);
        if (from == null || to == null) return;

        double[] a = getAnchorPoint(from, c.fromAnchor);
        double[] b = getAnchorPoint(to, c.toAnchor);

        StringBuilder out = ctx.body();
        out.append(String.format(Locale.ROOT, "<polyline points=\"%.2f,%.2f %.2f,%.2f\" fill=\"none\" stroke=\"%s\" stroke-width=\"%.2f\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>\n",
                a[0], a[1], b[0], b[1], c.stroke, c.strokeWidth));

        if (c.isArrow) {
            double angle = Math.atan2(b[1] - a[1], b[0] - a[0]);
            double len = 12;
            double spread = 0.45;
            double p1x = b[0] + len * Math.cos(angle + Math.PI - spread);
            double p1y = b[1] + len * Math.sin(angle + Math.PI - spread);
            double p2x = b[0] + len * Math.cos(angle + Math.PI + spread);
            double p2y = b[1] + len * Math.sin(angle + Math.PI + spread);
            out.append(String.format(Locale.ROOT, "<polyline points=\"%.2f,%.2f %.2f,%.2f %.2f,%.2f\" fill=\"none\" stroke=\"%s\" stroke-width=\"%.2f\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>\n",
                    p1x, p1y, b[0], b[1], p2x, p2y, c.stroke, c.strokeWidth));
        }
    }

    private Element findElement(SvgContext ctx, String id) {
        return ctx.slide().elements.stream().filter(e -> e.id.equals(id)).findFirst().orElse(null);
    }

    private double[] getAnchorPoint(Element el, String side) {
        double cx = el.x + el.width / 2;
        double cy = el.y + el.height / 2;
        double halfW = el.width / 2;
        double halfH = el.height / 2;
        double offset = 8; // ANCHOR_OFFSET
        double lx = 0, ly = 0;
        if ("top".equals(side)) { lx = 0; ly = -halfH - offset; }
        else if ("right".equals(side)) { lx = halfW + offset; ly = 0; }
        else if ("bottom".equals(side)) { lx = 0; ly = halfH + offset; }
        else { lx = -halfW - offset; ly = 0; }

        double rot = Math.toRadians(el.rotation);
        return new double[]{
                cx + lx * Math.cos(rot) - ly * Math.sin(rot),
                cy + lx * Math.sin(rot) + ly * Math.cos(rot)
        };
    }
}