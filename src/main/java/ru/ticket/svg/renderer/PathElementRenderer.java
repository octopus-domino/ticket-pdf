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
        // КЛЮЧЕВОЙ ФИКС: translate + rotate (как Konva.Group).
        // Без translate path с локальными координатами (M 0 0 L 100 0 …)
        // рисуется в левом верхнем углу — «улетает влево».
        String tr = String.format(Locale.ROOT,
                " transform=\"translate(%.2f,%.2f) rotate(%.2f)\"",
                p.x, p.y, p.rotation);

        StringBuilder out = ctx.body();
        out.append("<g").append(tr).append(">\n");
        out.append(String.format(Locale.ROOT,
                "<path d=\"%s\" fill=\"%s\" stroke=\"%s\" stroke-width=\"%.2f\" " +
                        "stroke-linecap=\"round\" stroke-linejoin=\"round\"/>\n",
                XmlEscape.attr(p.d),
                p.fill == null ? "none" : p.fill,
                p.stroke == null ? "none" : p.stroke,
                p.strokeWidth));
        out.append("</g>\n");
    }
}