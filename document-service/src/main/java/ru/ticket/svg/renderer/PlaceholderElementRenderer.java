package ru.ticket.svg.renderer;

import ru.ticket.dto.Padding;
import ru.ticket.dto.PlaceholderElement;
import ru.ticket.svg.SvgContext;
import ru.ticket.svg.SvgSerializationUtil;
import ru.ticket.text.TextLayoutService;
import ru.ticket.util.XmlEscape;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class PlaceholderElementRenderer implements ElementRenderer<PlaceholderElement> {

    private final TextLayoutService textLayout;

    public PlaceholderElementRenderer(TextLayoutService textLayout) {
        this.textLayout = textLayout;
    }

    @Override
    public Class<PlaceholderElement> supports() { return PlaceholderElement.class; }

    @Override
    public void render(PlaceholderElement p, SvgContext ctx) {
        StringBuilder out = ctx.body();

        String tr = p.rotation != 0
                ? String.format(Locale.ROOT, " transform=\"rotate(%.2f %.2f %.2f)\"",
                p.rotation, p.x, p.y)
                : "";

        out.append("<g").append(tr).append(">\n");

        if ("image".equals(p.fieldType)) {
            renderImage(out, p);
        } else {
            renderText(out, p);
        }

        out.append("</g>\n");
    }

    private void renderImage(StringBuilder out, PlaceholderElement p) {
        if (p.sample == null || p.sample.isEmpty()) return;
        out.append(SvgSerializationUtil.imageTag(
                p.sample, p.x, p.y, p.width, p.height,
                " preserveAspectRatio=\"xMidYMid meet\""));
    }

    private void renderText(StringBuilder out, PlaceholderElement p) {
        String txt = p.sample != null && !p.sample.isEmpty()
                ? p.sample
                : "{{" + p.fieldPath + "}}";

        Padding pad = p.padding != null ? p.padding : new Padding();
        double innerW = Math.max(10, p.width - pad.left - pad.right);
        double lineH = p.fontSize * (p.lineHeight > 0 ? p.lineHeight : 1.2);
        double startY = p.y + pad.top + p.fontSize;

        List<String> lines = textLayout.wrap(
                txt, innerW, p.fontFamily, (int) Math.round(p.fontSize), p.fontStyle);

        for (int i = 0; i < lines.size(); i++) {
            out.append(String.format(Locale.ROOT,
                    "<text x=\"%.2f\" y=\"%.2f\" font-size=\"%.2f\" font-family=\"%s\" " +
                            "fill=\"%s\" xml:space=\"preserve\">%s</text>\n",
                    p.x + pad.left, startY + i * lineH, p.fontSize,
                    XmlEscape.attr(p.fontFamily), p.fill,
                    XmlEscape.escape(lines.get(i))));
        }
    }
}