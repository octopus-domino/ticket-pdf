package ru.ticket.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import org.springframework.stereotype.Component;
import ru.ticket.dto.TextElement;
import ru.ticket.util.XmlEscape;

/** Uses source-guarded canvas positions; changed bindings fall back to normal layout. */
@Component
public class BrowserTextLayoutRenderer {
    private final ObjectMapper mapper;
    public BrowserTextLayoutRenderer(ObjectMapper mapper) { this.mapper = mapper; }
    public boolean render(StringBuilder out, TextElement text) {
        JsonNode layout = text.browserLayout;
        if (layout == null || !layout.path("source").isObject() || !layout.path("runs").isArray() || layout.path("runs").size() > 10000) return false;
        JsonNode current = mapper.valueToTree(text);
        var source = layout.path("source");
        for (String key : new String[]{"text","html","width","height","fontSize","fontFamily","fontStyle","fill","textDecoration","align","verticalAlign","lineHeight","padding"}) {
            if (!equivalent(source.path(key), current.path(key))) return false;
        }
        var rendered = new StringBuilder();
        for (JsonNode run : layout.path("runs")) {
            double x = run.path("x").asDouble(Double.NaN), y = run.path("y").asDouble(Double.NaN);
            double width = run.path("width").asDouble(Double.NaN), size = run.path("size").asDouble(Double.NaN);
            if (!Double.isFinite(x + y + width + size) || width < 0 || size <= 0 || size > 1024 || Math.abs(x) > 100000 || Math.abs(y) > 100000 || width > 100000) return false;
            String color=run.path("color").asText(text.fill);
            if(!safeColor(color)) return false;
            String background=run.path("background").asText("");
            if(!background.isEmpty() && !background.equals("null") && safeColor(background)) {
                rendered.append(String.format(Locale.ROOT,"<rect x=\"%.4f\" y=\"%.4f\" width=\"%.4f\" height=\"%.4f\" fill=\"%s\"/>\n",x,y-size*.8,width,size*1.2,XmlEscape.attr(background)));
            }
            String decoration = (run.path("underline").asBoolean() ? "underline " : "") + (run.path("strike").asBoolean() ? "line-through" : "");
            rendered.append(String.format(Locale.ROOT, "<text x=\"%.4f\" y=\"%.4f\" font-size=\"%.4f\" font-family=\"%s\" font-weight=\"%s\" font-style=\"%s\" fill=\"%s\" text-decoration=\"%s\" xml:space=\"preserve\"%s>%s</text>\n",
                    x, y, size, XmlEscape.attr(run.path("family").asText("Arial")), run.path("bold").asBoolean() ? "700" : "400", run.path("italic").asBoolean() ? "italic" : "normal", XmlEscape.attr(color), decoration.trim(),
                    width > 0 ? String.format(Locale.ROOT, " textLength=\"%.4f\" lengthAdjust=\"spacingAndGlyphs\"", width) : "", XmlEscape.escape(run.path("text").asText())));
        }
        out.append(rendered);
        return true;
    }
    private static boolean safeColor(String value) {
        return value!=null && value.matches("(?i)(#[a-f0-9]{3,8}|[a-z]{1,24}|rgba?\\([0-9.,% ]{1,60}\\))");
    }
    private boolean equivalent(JsonNode a, JsonNode b) {
        if ((a.isNull() || a.isMissingNode()) && (b.isNull() || b.isMissingNode())) return true;
        if (a.isNumber() && b.isNumber()) return Math.abs(a.asDouble() - b.asDouble()) < .000001;
        if (a.isObject() && b.isObject()) {
            var fields = a.fieldNames();
            while (fields.hasNext()) { String key = fields.next(); if (!equivalent(a.path(key), b.path(key))) return false; }
            return a.size() == b.size();
        }
        return a.equals(b);
    }
}
