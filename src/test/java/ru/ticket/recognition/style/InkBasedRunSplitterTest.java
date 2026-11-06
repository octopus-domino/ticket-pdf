package ru.ticket.recognition.style;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.ticket.recognition.OcrEngine.Word;

class InkBasedRunSplitterTest {

    private static final BufferedImage DUMMY =
            new BufferedImage(200, 50, BufferedImage.TYPE_INT_RGB);

    /** Заглушка анализатора: стиль определяется по тексту слова. */
    private static final class StubAnalyzer implements TextStyleAnalyzer {
        private final Map<String, TextStyle> styles;
        StubAnalyzer(Map<String, TextStyle> styles) { this.styles = styles; }
        @Override public TextStyle analyze(BufferedImage image, Word word) {
            return styles.getOrDefault(word.text(),
                    TextStyle.defaultFor("#202020", 14));
        }
    }

    private static Word word(String text, int x, int w) {
        return new Word(text, x, 0, w, 20, 95, "L1");
    }

    @Test
    void mergesAdjacentWordsWithSameStyle() {
        var style = new TextStyle("#000000", 14, false, false, false, false, "Arial");
        var splitter = new InkBasedRunSplitter(
                new StubAnalyzer(Map.of("Hello", style, "world", style)));

        var runs = splitter.split(DUMMY, List.of(word("Hello", 0, 50), word("world", 55, 60)));

        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).word().text()).isEqualTo("Hello world");
    }

    @Test
    void separatesDifferentColors() {
        var black = new TextStyle("#000000", 14, false, false, false, false, "Arial");
        var red   = new TextStyle("#ff0000", 14, false, false, false, false, "Arial");
        var splitter = new InkBasedRunSplitter(
                new StubAnalyzer(Map.of("Hello", black, "world", red)));

        var runs = splitter.split(DUMMY, List.of(word("Hello", 0, 50), word("world", 55, 60)));

        assertThat(runs).hasSize(2);
        assertThat(runs.get(0).style().color()).isEqualTo("#000000");
        assertThat(runs.get(1).style().color()).isEqualTo("#ff0000");
    }

    @Test
    void separatesBoldFromRegular() {
        var regular = new TextStyle("#000000", 14, false, false, false, false, "Arial");
        var bold    = new TextStyle("#000000", 14, true,  false, false, false, "Arial");
        var splitter = new InkBasedRunSplitter(
                new StubAnalyzer(Map.of("Hello", regular, "world", bold)));

        var runs = splitter.split(DUMMY, List.of(word("Hello", 0, 50), word("world", 55, 60)));

        assertThat(runs).hasSize(2);
        assertThat(runs.get(0).style().bold()).isFalse();
        assertThat(runs.get(1).style().bold()).isTrue();
    }

    @Test
    void smallFontSizeJitterDoesNotSplit() {
        var s1 = new TextStyle("#000000", 14.0, false, false, false, false, "Arial");
        var s2 = new TextStyle("#000000", 15.0, false, false, false, false, "Arial");
        var splitter = new InkBasedRunSplitter(
                new StubAnalyzer(Map.of("Hello", s1, "world", s2)));

        var runs = splitter.split(DUMMY, List.of(word("Hello", 0, 50), word("world", 55, 60)));

        // Порог 15% от 14 = 2.1 > разница 1.0 → сливаем
        assertThat(runs).hasSize(1);
    }

    @Test
    void emptyInputReturnsEmptyList() {
        var splitter = new InkBasedRunSplitter(new StubAnalyzer(Map.of()));
        assertThat(splitter.split(DUMMY, List.of())).isEmpty();
    }

    @Test
    void orderIsLeftToRightRegardlessOfInput() {
        var style = new TextStyle("#000000", 14, false, false, false, false, "Arial");
        var splitter = new InkBasedRunSplitter(
                new StubAnalyzer(Map.of("first", style, "second", style)));

        var runs = splitter.split(DUMMY, List.of(
                word("second", 100, 60),
                word("first",   10, 50)));

        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).word().text()).isEqualTo("first second");
    }

    @Test
    void analyzerExceptionFallsBackToSafeDefault() {
        TextStyleAnalyzer throwing = (image, word) -> {
            throw new IllegalStateException("boom");
        };
        var splitter = new InkBasedRunSplitter(throwing);

        var runs = splitter.split(DUMMY, List.of(word("Hello", 0, 50)));

        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).style().fontFamily()).isEqualTo("Arial");
        assertThat(runs.get(0).style().fontSize()).isGreaterThan(0);
    }
}