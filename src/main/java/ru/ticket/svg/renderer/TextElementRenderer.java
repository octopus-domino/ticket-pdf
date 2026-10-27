package ru.ticket.svg.renderer;

import ru.ticket.dto.Padding;
import ru.ticket.dto.TextElement;
import ru.ticket.svg.SvgContext;
import ru.ticket.text.TextLayoutService;
import ru.ticket.text.richtext.ArcTextSvgWriter;
import ru.ticket.text.richtext.RichTextSvgRenderer;
import ru.ticket.util.XmlEscape;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class TextElementRenderer implements ElementRenderer<TextElement> {

    private final TextLayoutService textLayout;
    private final RichTextSvgRenderer richRenderer;
    private final ArcTextSvgWriter arcWriter;

    public TextElementRenderer(TextLayoutService textLayout,
                               RichTextSvgRenderer richRenderer,
                               ArcTextSvgWriter arcWriter) {
        this.textLayout = textLayout;
        this.richRenderer = richRenderer;
        this.arcWriter = arcWriter;
    }

    @Override
    public Class<TextElement> supports() { return TextElement.class; }

    @Override
    public void render(TextElement t, SvgContext ctx) {
        StringBuilder out = ctx.body();

        String tr = String.format(Locale.ROOT,
                " transform=\"translate(%.2f,%.2f) rotate(%.2f)\"",
                t.x, t.y, t.rotation);

        out.append("<g").append(tr).append(">\n");

        if (t.background != null && !"transparent".equals(t.background)) {
            out.append(String.format(Locale.ROOT,
                    "<rect x=\"0\" y=\"0\" width=\"%.2f\" height=\"%.2f\" fill=\"%s\"/>\n",
                    t.width, t.height, t.background));
        }

        if (isArc(t)) {
            renderArc(out, ctx, t);
        } else if (isRich(t)) {
            richRenderer.render(out, t);
        } else {
            renderPlain(out, t);
        }

        out.append("</g>\n");
    }

    private boolean isArc(TextElement t) {
        return "arc".equals(t.textMode);
    }

    private boolean isRich(TextElement t) {
        return t.html != null && !t.html.isBlank();
    }

    private void renderArc(StringBuilder out, SvgContext ctx, TextElement t) {
        double radius = t.arcRadius != null && t.arcRadius > 0 ? t.arcRadius : 150.0;
        double startDeg = t.arcStartAngle != null ? t.arcStartAngle : -90.0;
        double endDeg = t.arcEndAngle != null ? t.arcEndAngle : 90.0;

        // Как в frontend TextEl (isArc): cx = width/2, cy = radius + height/2.
        double cx = t.width / 2.0;
        double cy = radius + t.height / 2.0;

        arcWriter.write(out, ctx,
                t.text == null ? "" : t.text,
                cx, cy, radius, startDeg, endDeg,
                t.fontSize, t.fontFamily, t.fontStyle, t.fill);
    }

    private void renderPlain(StringBuilder out, TextElement t) {
        Padding pad = t.padding != null ? t.padding : new Padding();
        double innerX = pad.left;
        double innerW = Math.max(10, t.width - pad.left - pad.right);
        double innerH = Math.max(10, t.height - pad.top - pad.bottom);
        double lineHeight = t.fontSize * (t.lineHeight > 0 ? t.lineHeight : 1.5);

        String anchor = "center".equals(t.align) ? "middle"
                : "right".equals(t.align)  ? "end"
                : "start";
        double tx = innerX + ("center".equals(t.align) ? innerW / 2
                : "right".equals(t.align)  ? innerW : 0);

        String fontWeight = isBold(t.fontStyle) ? "700" : "400";
        String fontStyle  = isItalic(t.fontStyle) ? "italic" : "normal";

        List<String> lines = textLayout.wrap(
                t.text == null ? "" : t.text,
                innerW, t.fontFamily, (int) Math.round(t.fontSize), t.fontStyle);

        double totalTextHeight = lines.size() * lineHeight;

        double offsetY = pad.top;
        String verticalAlign = t.verticalAlign != null ? t.verticalAlign : "top";
        if ("middle".equals(verticalAlign)) {
            offsetY = pad.top + Math.max(0, (innerH - totalTextHeight) / 2);
        } else if ("bottom".equals(verticalAlign)) {
            offsetY = pad.top + Math.max(0, innerH - totalTextHeight);
        }

        double startY = offsetY + t.fontSize * 0.8;

        for (int i = 0; i < lines.size(); i++) {
            out.append(String.format(Locale.ROOT,
                    "<text x=\"%.2f\" y=\"%.2f\" font-size=\"%.2f\" font-family=\"%s\" " +
                            "font-weight=\"%s\" font-style=\"%s\" fill=\"%s\" text-anchor=\"%s\" " +
                            "xml:space=\"preserve\">%s</text>\n",
                    tx, startY + i * lineHeight,
                    t.fontSize, XmlEscape.attr(t.fontFamily), fontWeight, fontStyle,
                    t.fill, anchor, XmlEscape.escape(lines.get(i))));
        }
    }

    private boolean isBold(String style)   { return style != null && style.contains("bold"); }
    private boolean isItalic(String style) { return style != null && style.contains("italic"); }
}