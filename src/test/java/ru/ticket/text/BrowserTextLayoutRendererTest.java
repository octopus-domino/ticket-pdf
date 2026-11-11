package ru.ticket.text;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import ru.ticket.dto.TextElement;

class BrowserTextLayoutRendererTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final BrowserTextLayoutRenderer renderer = new BrowserTextLayoutRenderer(mapper);

    @Test
    void preservesOneCanvasLineAndEscapesText() {
        TextElement text = snapshot();
        StringBuilder output = new StringBuilder();
        assertThat(renderer.render(output, text)).isTrue();
        assertThat(output.toString()).contains("textLength=\"179.0000\"", "A &amp; B", "y=\"14.0000\"");
        assertThat(output.toString().split("<text ")).hasSize(2);
    }

    @Test
    void changedBindingsOrGeometryCannotReuseStalePositions() {
        TextElement text = snapshot();
        text.text = "A much longer personalized name";
        StringBuilder output = new StringBuilder();
        assertThat(renderer.render(output, text)).isFalse();
        assertThat(output).isEmpty();
        text = snapshot();
        text.width++;
        assertThat(renderer.render(output, text)).isFalse();
    }

    @Test
    void invalidRunRejectsWholeSnapshotWithoutPartialOutput() {
        TextElement text = snapshot();
        ((ObjectNode) text.browserLayout.path("runs").get(0)).put("color", "url(https://example.test/image)");
        StringBuilder output = new StringBuilder();
        assertThat(renderer.render(output, text)).isFalse();
        assertThat(output).isEmpty();
    }

    private TextElement snapshot() {
        TextElement text = new TextElement();
        text.text = "A & B";
        text.width = 180;
        text.height = 24;
        ObjectNode layout = mapper.createObjectNode();
        ObjectNode source = mapper.valueToTree(text);
        ObjectNode guarded = layout.putObject("source");
        for (String key : new String[]{"text", "html", "width", "height", "fontSize", "fontFamily", "fontStyle", "fill", "textDecoration", "align", "verticalAlign", "lineHeight", "padding"}) {
            guarded.set(key, source.path(key));
        }
        layout.putArray("runs").addObject().put("text", text.text).put("x", 0).put("y", 14)
                .put("width", 179).put("size", 14).put("family", "Arial").put("color", "#123456");
        text.browserLayout = layout;
        return text;
    }
}
