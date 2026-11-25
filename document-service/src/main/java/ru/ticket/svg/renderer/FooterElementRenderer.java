package ru.ticket.svg.renderer;

import ru.ticket.dto.FooterElement;
import ru.ticket.svg.SvgContext;
import ru.ticket.util.XmlEscape;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class FooterElementRenderer implements ElementRenderer<FooterElement> {

    @Override
    public Class<FooterElement> supports() { return FooterElement.class; }

    @Override
    public void render(FooterElement f, SvgContext ctx) {
        StringBuilder out = ctx.body();

        String tr = f.rotation != 0
                ? String.format(Locale.ROOT, " transform=\"rotate(%.2f %.2f %.2f)\"",
                f.rotation, f.x, f.y)
                : "";

        out.append("<g").append(tr).append(">\n");
        out.append(String.format(Locale.ROOT,
                "<rect x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\" fill=\"%s\"/>\n",
                f.x, f.y, f.width, f.height, f.bgColor));
        out.append(String.format(Locale.ROOT,
                "<text x=\"%.2f\" y=\"%.2f\" font-size=\"%.2f\" fill=\"%s\" " +
                        "text-anchor=\"middle\" xml:space=\"preserve\">%s</text>\n",
                f.x + f.width / 2,
                f.y + f.height / 2 + f.fontSize * 0.35,
                f.fontSize, f.fill,
                XmlEscape.escape(f.text == null ? "" : f.text)));
        out.append("</g>\n");
    }
}