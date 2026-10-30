package ru.ticket.svg.renderer;

import org.junit.jupiter.api.Test;
import ru.ticket.dto.DiagramElement;
import ru.ticket.dto.Slide;
import ru.ticket.svg.SvgContext;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Юнит-тесты для {@link DiagramElementRenderer}.

 * Покрывают:
 *  - пустой SVG → плейсхолдер;
 *  - nested SVG → разворачивается в <svg> с координатами;
 *  - data URL с base64 → декодируется;
 *  - мусор на входе → плейсхолдер.
 */
class DiagramElementRendererTest {

    private final DiagramElementRenderer renderer = new DiagramElementRenderer();

    private SvgContext ctx() {
        Slide slide = new Slide();
        slide.elements = new ArrayList<>();
        return new SvgContext(slide);
    }

    private DiagramElement diagram(String svg) {
        DiagramElement d = new DiagramElement();
        d.id = "d1";
        d.x = 10;
        d.y = 20;
        d.width = 200;
        d.height = 100;
        d.svg = svg;
        d.xml = "";
        return d;
    }

    @Test
    void rendersPlaceholderForEmptySvg() {
        SvgContext ctx = ctx();
        renderer.render(diagram(""), ctx);

        String body = ctx.body().toString();
        assertThat(body)
                .contains("<rect")
                .contains("stroke-dasharray");
    }

    @Test
    void unwrapsNestedSvgIntoPlainSvg() {
        String svg = "<?xml version=\"1.0\"?>" +
                "<svg xmlns=\"http://www.w3.org/2000/svg\">" +
                "  <g><rect x=\"0\" y=\"0\" width=\"10\" height=\"10\"/></g>" +
                "</svg>";

        SvgContext ctx = ctx();
        renderer.render(diagram(svg), ctx);

        String body = ctx.body().toString();

        // Batik не рендерит nested SVG в <image> — проверяем, что мы не вложили в <image>.
        assertThat(body).doesNotContain("<image href=\"data:image/svg+xml");

        // Должен быть вложенный <svg> с нашими координатами.
        assertThat(body)
                .contains("<svg ")
                .contains("x=\"10.00\"")
                .contains("y=\"20.00\"")
                .contains("width=\"200.00\"")
                .contains("height=\"100.00\"")
                .contains("<rect");
    }

    @Test
    void decodesBase64DataUrl() {
        String inner = "<svg xmlns=\"http://www.w3.org/2000/svg\">" +
                "<g><circle r=\"5\"/></g>" +
                "</svg>";
        String b64 = Base64.getEncoder().encodeToString(inner.getBytes(StandardCharsets.UTF_8));

        SvgContext ctx = ctx();
        renderer.render(diagram("data:image/svg+xml;base64," + b64), ctx);

        String body = ctx.body().toString();
        assertThat(body).contains("<circle");
    }

    @Test
    void rendersPlaceholderForInvalidSvg() {
        SvgContext ctx = ctx();
        renderer.render(diagram("garbage"), ctx);

        String body = ctx.body().toString();
        assertThat(body)
                .contains("<rect")
                .contains("stroke-dasharray");
    }
}