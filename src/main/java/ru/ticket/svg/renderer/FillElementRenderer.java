package ru.ticket.svg.renderer;

import ru.ticket.dto.FillElement;
import ru.ticket.svg.SvgContext;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class FillElementRenderer implements ElementRenderer<FillElement> {

    @Override
    public Class<FillElement> supports() { return FillElement.class; }

    @Override
    public void render(FillElement f, SvgContext ctx) {
        if (f.points == null || f.points.length < 6) return;

        StringBuilder pts = new StringBuilder();
        for (int i = 0; i + 1 < f.points.length; i += 2) {
            if (i > 0) pts.append(' ');
            pts.append(String.format(Locale.ROOT, "%.2f,%.2f",
                    f.points[i], f.points[i + 1]));
        }

        String tr = String.format(Locale.ROOT,
                " transform=\"translate(%.2f,%.2f) rotate(%.2f)\"",
                f.x, f.y, f.rotation);

        ctx.body().append(String.format(Locale.ROOT,
                "<polygon points=\"%s\" fill=\"%s\" fill-opacity=\"%.2f\"%s/>\n",
                pts, f.fill, f.opacity, tr));
    }
}