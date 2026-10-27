package ru.ticket.svg.renderer;

import ru.ticket.dto.DiagramElement;
import ru.ticket.svg.SvgContext;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/**
 * Рендерит draw.io-диаграмму в SVG-документ.
 *
 * Способ: вложенный SVG через data URL в <image>.
 * Batik PDFTranscoder поддерживает
 * <image href="data:image/svg+xml;base64,...">, поэтому
 * диаграмма сохраняет вектор в PDF.
 *
 * Если svg пуст — рисуем placeholder (как frontend DiagramEl).
 */
@Component
public class DiagramElementRenderer implements ElementRenderer<DiagramElement> {

    @Override
    public Class<DiagramElement> supports() { return DiagramElement.class; }

    @Override
    public void render(DiagramElement d, SvgContext ctx) {
        StringBuilder out = ctx.body();

        String tr = d.rotation != 0
                ? String.format(Locale.ROOT,
                " transform=\"rotate(%.2f %.2f %.2f)\"",
                d.rotation, d.x, d.y)
                : "";

        out.append("<g").append(tr).append(">\n");

        if (d.svg == null || d.svg.isBlank()) {
            out.append(String.format(Locale.ROOT,
                    "<rect x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\" " +
                            "fill=\"#f8fafc\" stroke=\"#cbd5e1\" stroke-width=\"1\" " +
                            "stroke-dasharray=\"6,4\"/>\n",
                    d.x, d.y, d.width, d.height));
        } else {
            byte[] bytes = d.svg.getBytes(StandardCharsets.UTF_8);
            String dataUrl = "data:image/svg+xml;base64,"
                    + Base64.getEncoder().encodeToString(bytes);

            out.append(String.format(Locale.ROOT,
                    "<image href=\"%s\" xlink:href=\"%s\" " +
                            "x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\" " +
                            "preserveAspectRatio=\"xMidYMid meet\"/>\n",
                    dataUrl, dataUrl,
                    d.x, d.y, d.width, d.height));
        }

        out.append("</g>\n");
    }
}