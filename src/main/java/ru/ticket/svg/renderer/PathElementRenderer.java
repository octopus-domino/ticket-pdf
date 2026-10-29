package ru.ticket.svg.renderer;

import ru.ticket.dto.PathElement;
import ru.ticket.svg.SvgContext;
import ru.ticket.util.XmlEscape;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class PathElementRenderer implements ElementRenderer<PathElement> {

    @Override
    public Class<PathElement> supports() { return PathElement.class; }

    @Override
    public void render(PathElement p, SvgContext ctx) {
        boolean flipX = Boolean.TRUE.equals(p.flipX);
        boolean flipY = Boolean.TRUE.equals(p.flipY);

        double tx = p.x + (flipX ? p.width : 0);
        double ty = p.y + (flipY ? p.height : 0);

        // -1 : 1 — это int. В %.2f Java ожидает double.
        // Без каста падает с IllegalFormatConversionException: f != java.lang.Integer.
        String tr = String.format(Locale.ROOT,
                " transform=\"translate(%.2f,%.2f) rotate(%.2f) scale(%.2f, %.2f)\"",
                (double) tx,
                (double) ty,
                (double) p.rotation,
                flipX ? -1.0 : 1.0,
                flipY ? -1.0 : 1.0);

        StringBuilder out = ctx.body();
        out.append("<g").append(tr).append(">\n");

        String fill = (p.fill == null || p.fill.isEmpty() || "transparent".equals(p.fill)) ? "none" : p.fill;
        String stroke = (p.stroke == null || p.stroke.isEmpty()) ? "none" : p.stroke;

        out.append(String.format(Locale.ROOT,
                "<path d=\"%s\" fill=\"%s\" stroke=\"%s\" stroke-width=\"%.2f\" " +
                        "stroke-linecap=\"round\" stroke-linejoin=\"round\"/>\n",
                XmlEscape.attr(p.d),
                fill,
                stroke,
                (double) p.strokeWidth));
        out.append("</g>\n");
    }
}