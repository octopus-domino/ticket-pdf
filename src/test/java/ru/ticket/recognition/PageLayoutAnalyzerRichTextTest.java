package ru.ticket.recognition;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.ticket.recognition.RecognitionDraft.Item;
import ru.ticket.recognition.style.StyledRun;
import ru.ticket.recognition.style.StyledRunSplitter;
import ru.ticket.recognition.style.TestImages;
import ru.ticket.recognition.style.TextStyle;

class PageLayoutAnalyzerRichTextTest {

    /** Заглушка: возвращает предопределённые runs, игнорируя изображение и строку. */
    private static final class FixedSplitter implements StyledRunSplitter {
        private final List<StyledRun> runs;
        FixedSplitter(List<StyledRun> runs) { this.runs = runs; }
        @Override
        public List<StyledRun> split(BufferedImage image, List<OcrEngine.Word> line) {
            return runs;
        }
    }

    private static OcrEngine.Word word(String text, int x, int w) {
        return new OcrEngine.Word(text, x, 0, w, 20, 95, "L1");
    }

    @Test
    void singleStyleProducesPlainTextElement() {
        var style = new TextStyle("#000000", 16, false, false, false, false, "Arial");
        var word = word("Hello world", 0, 100);
        var analyzer = new PageLayoutAnalyzer(
                new FixedSplitter(List.of(new StyledRun(word, style))));

        var draft = analyzer.analyze(TestImages.blank(200, 50, Color.WHITE), List.of(word));

        var textItems = textItems(draft.elements());
        assertThat(textItems).isNotEmpty();

        Map<String, Object> props = textItems.get(0).properties();
        assertThat(props.get("richText")).isEqualTo(false);
        assertThat(props.get("text")).isEqualTo("Hello world");
        assertThat(props.get("fontSize")).isEqualTo(16.0);
        assertThat(props.get("fill")).isEqualTo("#000000");
        assertThat(props.get("fontFamily")).isEqualTo("Arial");
    }

    @Test
    void twoStylesProduceRichHtmlElement() {
        var black = new TextStyle("#000000", 16, false, false, false, false, "Arial");
        var red   = new TextStyle("#ff0000", 16, true,  false, false, false, "Arial");
        var w1 = word("Hello", 0, 50);
        var w2 = word("world", 55, 60);
        var analyzer = new PageLayoutAnalyzer(
                new FixedSplitter(List.of(
                        new StyledRun(w1, black),
                        new StyledRun(w2, red))));

        var draft = analyzer.analyze(TestImages.blank(200, 50, Color.WHITE), List.of(w1, w2));

        var textItems = textItems(draft.elements());
        assertThat(textItems).isNotEmpty();

        Map<String, Object> props = textItems.get(0).properties();
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
        var underlined = new TextStyle("#000000", 14, false, false, true,  false, "Arial");
        var struck    = new TextStyle("#000000", 14, false, false, false, true,  "Arial");
        var w1 = word("under", 0, 50);
        var w2 = word("strike", 55, 60);
        var analyzer = new PageLayoutAnalyzer(
                new FixedSplitter(List.of(
                        new StyledRun(w1, underlined),
                        new StyledRun(w2, struck))));

        var draft = analyzer.analyze(TestImages.blank(200, 50, Color.WHITE), List.of(w1, w2));

        String html = (String) textItems(draft.elements()).get(0).properties().get("html");
        assertThat(html).contains("text-decoration:underline");
        assertThat(html).contains("text-decoration:line-through");
    }

    @Test
    void emptyWordListProducesNoTextItems() {
        var analyzer = new PageLayoutAnalyzer(new FixedSplitter(List.of()));
        var draft = analyzer.analyze(TestImages.blank(100, 50, Color.WHITE), List.of());
        assertThat(textItems(draft.elements())).isEmpty();
    }

    private static List<Item> textItems(List<Item> all) {
        return all.stream().filter(i -> "text".equals(i.type())).toList();
    }
}