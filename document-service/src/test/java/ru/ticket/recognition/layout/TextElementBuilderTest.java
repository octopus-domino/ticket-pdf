package ru.ticket.recognition.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.ticket.recognition.OcrEngine.Word;
import ru.ticket.recognition.RecognitionDraft.Item;
import ru.ticket.recognition.style.StyledRun;
import ru.ticket.recognition.style.StyledRunSplitter;
import ru.ticket.recognition.style.TextStyle;

class TextElementBuilderTest {
    @Test void richTextKeepsDifferentRunSizesAndExplicitRegularStyle() {
        var heading = new TextStyle("#ffffff", 28, true, true, true, false, "Arial");
        var body = new TextStyle("#ffffff", 13, false, false, false, false, "Arial");
        var w1 = word("Heading", 0, 80);
        var w2 = word("body", 85, 40);
        var builder = new TextElementBuilder(new FixedSplitter(List.of(new StyledRun(w1, heading), new StyledRun(w2, body))));
        String html = (String)builder.build(DUMMY, List.of(w1, w2), 200, 50).properties().get("html");
        assertThat(html).contains("font-size:28.0px", "font-size:13.0px", "font-weight:400", "font-style:normal", "text-decoration:none");
    }

    private static final BufferedImage DUMMY =
            new BufferedImage(200, 50, BufferedImage.TYPE_INT_RGB);

    private static final class FixedSplitter implements StyledRunSplitter {
        private final List<StyledRun> runs;
        FixedSplitter(List<StyledRun> runs) { this.runs = runs; }
        @Override
        public List<StyledRun> split(BufferedImage image, List<Word> line) {
            return runs;
        }
    }

    private static Word word(String text, int x, int w) {
        return new Word(text, x, 0, w, 20, 95, "L1");
    }

    @Test
    void singleStyleProducesPlainTextElement() {
        var style = new TextStyle("#000000", 16, false, false, false, false, "Arial");
        var w = word("Hello world", 0, 100);
        var builder = new TextElementBuilder(
                new FixedSplitter(List.of(new StyledRun(w, style))));

        Item item = builder.build(DUMMY, List.of(w), 200, 50);

        assertThat(item).isNotNull();
        Map<String, Object> props = item.properties();
        assertThat(props.get("richText")).isEqualTo(false);
        assertThat(props.get("text")).isEqualTo("Hello world");
        assertThat(props.get("fontSize")).isEqualTo(16.0);
        assertThat(props.get("fill")).isEqualTo("#000000");
        assertThat(props.get("fontFamily")).isEqualTo("Arial");
    }

    @Test
    void multipleStylesProduceRichHtml() {
        var black = new TextStyle("#000000", 16, false, false, false, false, "Arial");
        var red = new TextStyle("#ff0000", 16, true, false, false, false, "Arial");
        var w1 = word("Hello", 0, 50);
        var w2 = word("world", 55, 60);
        var builder = new TextElementBuilder(
                new FixedSplitter(List.of(
                        new StyledRun(w1, black),
                        new StyledRun(w2, red))));

        Item item = builder.build(DUMMY, List.of(w1, w2), 200, 50);

        assertThat(item).isNotNull();
        Map<String, Object> props = item.properties();
        assertThat(props.get("richText")).isEqualTo(true);
        String html = (String) props.get("html");
        assertThat(html).contains("color:#000000");
        assertThat(html).contains("color:#ff0000");
        assertThat(html).contains("font-weight:700");
        assertThat(html).contains(">Hello<");
        assertThat(html).contains(">world<");
        assertThat(props.get("text")).isEqualTo("Hello world");
    }

    @Test
    void underlineAndStrikeThroughSurviveRichHtml() {
        var underlined = new TextStyle("#000000", 14, false, false, true, false, "Arial");
        var struck = new TextStyle("#000000", 14, false, false, false, true, "Arial");
        var w1 = word("under", 0, 50);
        var w2 = word("strike", 55, 60);
        var builder = new TextElementBuilder(
                new FixedSplitter(List.of(
                        new StyledRun(w1, underlined),
                        new StyledRun(w2, struck))));

        Item item = builder.build(DUMMY, List.of(w1, w2), 200, 50);
        String html = (String) item.properties().get("html");
        assertThat(html).contains("text-decoration:underline");
        assertThat(html).contains("text-decoration:line-through");
    }

    @Test
    void emptyLineReturnsNull() {
        var builder = new TextElementBuilder(new FixedSplitter(List.of()));
        assertThat(builder.build(DUMMY, List.of(), 200, 50)).isNull();
    }

    @Test
    void splitterExceptionFallsBackToPlainText() {
        var throwing = new StyledRunSplitter() {
            @Override
            public List<StyledRun> split(BufferedImage image, List<Word> line) {
                throw new IllegalStateException("boom");
            }
        };
        var builder = new TextElementBuilder(throwing);
        Item item = builder.build(DUMMY, List.of(word("Hello", 0, 50)), 200, 50);
        assertThat(item).isNotNull();
        assertThat(item.properties().get("richText")).isEqualTo(false);
        assertThat(item.properties().get("text")).isEqualTo("Hello");
    }
}
