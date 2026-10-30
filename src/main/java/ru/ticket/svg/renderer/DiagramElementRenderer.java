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

/**
 * Рендерит draw.io-диаграмму в SVG-документ.
 *
 * Проблема: Batik PDFTranscoder НЕ поддерживает nested SVG внутри {@code <image>}.
 * Он понимает только {@code <image href="data:image/png;base64,...">} или
 * {@code <image href="http://...">} — но не {@code data:image/svg+xml}.
 *
 * Решение: если в {@code el.svg} лежит base64-encoded SVG (как отдаёт фронтенд
 * в toSvgDiagram), мы:
 *   1) декодируем SVG-строку;
 *   2) разбираем её jsoup'ом;
 *   3) вытаскиваем {@code <svg>...</svg>} без пролога и DOCTYPE;
 *   4) вставляем как nested {@code <svg>} внутрь нашего — с {@code x/y/width/height}.
 *
 * Вложенные SVG (не в {@code <image>}) Batik рендерит корректно.
 */
@Component
public class DiagramElementRenderer implements ElementRenderer<DiagramElement> {

    private static final String SVG_NS = "http://www.w3.org/2000/svg";

    @Override
    public Class<DiagramElement> supports() {
        return DiagramElement.class;
    }

    @Override
    public void render(DiagramElement d, SvgContext ctx) {
        StringBuilder out = ctx.body();

        String tr = d.rotation != 0
                ? String.format(Locale.ROOT,
                " transform=\"rotate(%.2f %.2f %.2f)\"",
                (double) d.rotation, (double) d.x, (double) d.y)
                : "";

        out.append("<g").append(tr).append(">\n");

        if (d.svg == null || d.svg.isBlank()) {
            out.append(String.format(Locale.ROOT,
                    "<rect x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\" " +
                            "fill=\"#f8fafc\" stroke=\"#cbd5e1\" stroke-width=\"1\" " +
                            "stroke-dasharray=\"6,4\"/>\n",
                    (double) d.x, (double) d.y, (double) d.width, (double) d.height));
            out.append("</g>\n");
            return;
        }

        String nested = sanitizeAndExtractSvg(d.svg);
        if (nested == null) {
            out.append(String.format(Locale.ROOT,
                    "<rect x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\" " +
                            "fill=\"#f8fafc\" stroke=\"#cbd5e1\" stroke-width=\"1\" " +
                            "stroke-dasharray=\"6,4\"/>\n",
                    (double) d.x, (double) d.y, (double) d.width, (double) d.height));
            out.append("</g>\n");
            return;
        }

        // Открываем nested <svg> с координатами и размерами.
        out.append(String.format(Locale.ROOT,
                "<svg xmlns=\"%s\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" " +
                        "x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\" " +
                        "viewBox=\"0 0 %.2f %.2f\" preserveAspectRatio=\"xMidYMid meet\">\n",
                SVG_NS,
                (double) d.x, (double) d.y,
                (double) d.width, (double) d.height,
                (double) d.width, (double) d.height));

        // Внутренности вложенного SVG (без внешнего тега).
        out.append(nested);
        out.append("\n</svg>\n");

        out.append("</g>\n");
    }

    /**
     * Возвращает внутренности {@code <svg>...</svg>} без пролога и DOCTYPE.
     *
     * Поддерживает три формы:
     *   1) «сырой» SVG (drawio отдаёт его после embedImagesInSvg) —
     *      "<svg xmlns=...><g>...</g></svg>";
     *   2) base64-encoded data URL — на случай, если фронтенд прислал так;
     *   3) пустая строка/мусор — вернёт null, вызывающий нарисует плейсхолдер.
     */
    private String sanitizeAndExtractSvg(String raw) {
        String s = raw.trim();

        // Случай 2: data URL с base64 (если кто-то на фронте сериализовал иначе).
        if (s.startsWith("data:image/svg+xml;base64,")) {
            try {
                byte[] decoded = Base64.getDecoder().decode(
                        s.substring("data:image/svg+xml;base64,".length()));
                s = new String(decoded, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        // Убираем пролог и DOCTYPE.
        s = s.replaceAll("(?is)<\\?xml[^>]*\\?>", "");
        s = s.replaceAll("(?is)<!DOCTYPE[^>]*>", "");
        s = s.trim();

        if (!s.startsWith("<svg")) return null;

        // jsoup с XML-парсером — достаёт <svg> и его потомков.
        Document doc;
        try {
            doc = Jsoup.parse(s, "", Parser.xmlParser());
        } catch (Exception e) {
            return null;
        }

        Element svgEl = doc.selectFirst("svg");
        if (svgEl == null) return null;

        // Внутренности без внешнего тега.
        return svgEl.html();
    }
}