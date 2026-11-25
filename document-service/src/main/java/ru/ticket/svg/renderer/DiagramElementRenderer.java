package ru.ticket.svg.renderer;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;
import ru.ticket.dto.DiagramElement;
import ru.ticket.svg.SvgContext;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

@Component
public class DiagramElementRenderer implements ElementRenderer<DiagramElement> {

    private static final String SVG_NS = "http://www.w3.org/2000/svg";
    private static final String XLINK_NS = "http://www.w3.org/1999/xlink";

    @Override
    public Class<DiagramElement> supports() {
        return DiagramElement.class;
    }

    @Override
    public void render(DiagramElement d, SvgContext ctx) {
        StringBuilder out = ctx.body();

        String tr = d.rotation != 0
                ? String.format(Locale.ROOT, " transform=\"rotate(%.2f %.2f %.2f)\"", d.rotation, d.x, d.y)
                : "";

        out.append("<g").append(tr).append(">\n");

        if (d.svg == null || d.svg.isBlank()) {
            appendPlaceholder(out, d);
            out.append("</g>\n");
            return;
        }

        Document doc = parseSvg(d.svg);
        Element svgEl = doc != null ? doc.selectFirst("svg") : null;

        if (svgEl == null) {
            appendPlaceholder(out, d);
            out.append("</g>\n");
            return;
        }

        // Настраиваем позицию и размеры оригинального SVG
        svgEl.attr("x", String.format(Locale.ROOT, "%.2f", d.x));
        svgEl.attr("y", String.format(Locale.ROOT, "%.2f", d.y));
        svgEl.attr("width", String.format(Locale.ROOT, "%.2f", d.width));
        svgEl.attr("height", String.format(Locale.ROOT, "%.2f", d.height));

        // Гарантируем наличие namespace'ов (нужно для Batik)
        svgEl.attr("xmlns", SVG_NS);
        svgEl.attr("xmlns:xlink", XLINK_NS);

        // Если нет preserveAspectRatio, ставим "вписать без искажений"
        if (!svgEl.hasAttr("preserveAspectRatio")) {
            svgEl.attr("preserveAspectRatio", "xMidYMid meet");
        }

        out.append(svgEl.outerHtml());
        out.append("\n</g>\n");
    }

    private void appendPlaceholder(StringBuilder out, DiagramElement d) {
        out.append(String.format(Locale.ROOT,
                "<rect x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\" " +
                        "fill=\"#f8fafc\" stroke=\"#cbd5e1\" stroke-width=\"1\" stroke-dasharray=\"6,4\"/>\n",
                d.x, d.y, d.width, d.height));
    }

    private Document parseSvg(String raw) {
        String s = raw.trim();

        // Декодируем, если пришёл data URL (draw.io часто так отдаёт)
        if (s.startsWith("data:image/svg+xml;base64,")) {
            try {
                byte[] decoded = Base64.getDecoder().decode(s.substring("data:image/svg+xml;base64,".length()));
                s = new String(decoded, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        s = s.replaceAll("(?is)<\\?xml[^>]*\\?>", "").replaceAll("(?is)<!DOCTYPE[^>]*>", "").trim();

        if (!s.startsWith("<svg")) return null;

        try {
            return Jsoup.parse(s, "", Parser.xmlParser());
        } catch (Exception e) {
            return null;
        }
    }
}