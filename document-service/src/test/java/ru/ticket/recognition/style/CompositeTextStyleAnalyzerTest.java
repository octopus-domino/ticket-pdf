package ru.ticket.recognition.style;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Font;
import org.junit.jupiter.api.Test;
import ru.ticket.recognition.OcrEngine.StyleHints;
import ru.ticket.recognition.OcrEngine.Word;
import ru.ticket.text.AwtTextLayoutService;

class CompositeTextStyleAnalyzerTest {

    private final CompositeTextStyleAnalyzer analyzer =
            new CompositeTextStyleAnalyzer(
                    new AwtFontMatcher(new AwtTextLayoutService()));

    @Test
    void unreliableOcrHintsDoNotOverrideClearlyRenderedGlyphs() {
        var rendered = TestImages.renderWord("Recognition", "Arial", 32,
                Font.PLAIN, Color.BLACK, 2, 2);
        Word word = new Word(
                rendered.text(), rendered.x(), rendered.y(),
                rendered.width(), rendered.height(), 95, "L1",
                new StyleHints("Georgia", 18.0, true, true));

        TextStyle style = analyzer.analyze(rendered.image(), word);

        assertThat(style.fontFamily()).isEqualTo("Arial");
        assertThat(style.fontSize()).isBetween(29.0, 35.0);
        assertThat(style.bold()).isFalse();
        assertThat(style.italic()).isFalse();
    }

    @Test
    void noHintsUsesVisualAnalysis() {
        var rendered = TestImages.renderWord("Hello", "SansSerif", 24,
                Font.PLAIN, Color.BLACK, 2, 2);
        Word word = rendered.toWord(95);

        TextStyle style = analyzer.analyze(rendered.image(), word);

        assertThat(style.fontFamily()).isNotBlank();
        assertThat(style.fontSize()).isPositive();
        assertThat(style.color()).matches("#[0-9a-f]{6}");
    }

    @Test
    void detectsRedInk() {
        var rendered = TestImages.renderWord("XXX", "SansSerif", 32,
                Font.PLAIN, Color.RED, 2, 2);
        Word word = rendered.toWord(95);

        TextStyle style = analyzer.analyze(rendered.image(), word);

        int r = Integer.parseInt(style.color().substring(1, 3), 16);
        int g = Integer.parseInt(style.color().substring(3, 5), 16);
        int b = Integer.parseInt(style.color().substring(5, 7), 16);
        assertThat(r).isGreaterThan(120);
        assertThat(g).isLessThan(100);
        assertThat(b).isLessThan(100);
    }
}
