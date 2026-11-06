package ru.ticket.recognition.style;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Font;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.ticket.recognition.OcrEngine.Word;
import ru.ticket.text.AwtTextLayoutService;

class AwtFontMatcherTest {

    private final AwtTextLayoutService textLayout = new AwtTextLayoutService();
    private final AwtFontMatcher matcher = new AwtFontMatcher(textLayout);

    @Test
    void emptyTextFallsBackToDefault() {
        Word word = new Word("", 0, 0, 10, 10, 0, "L1");
        var profile = new VisualFontProfile(1, 0, 0.5, 0);
        assertThat(matcher.match(word, profile)).isEqualTo("Arial");
    }

    @Test
    void singleCandidateAlwaysWins() {
        AwtFontMatcher single =
                new AwtFontMatcher(textLayout, List.of("Courier New"));
        var rendered = TestImages.renderWord("test", "Courier New", 20,
                Font.PLAIN, Color.BLACK, 2, 2);
        Word word = rendered.toWord(95);
        var profile = single.profile(word, rendered.image());
        assertThat(single.match(word, profile)).isEqualTo("Courier New");
    }

    @Test
    void estimateFontSizeStaysInReasonableRange() {
        var rendered = TestImages.renderWord("Hello", "SansSerif", 24,
                Font.PLAIN, Color.BLACK, 2, 2);
        Word word = rendered.toWord(95);
        var profile = matcher.profile(word, rendered.image());
        double estimate = AwtFontMatcher.estimateFontSize(word, profile);
        assertThat(estimate).isBetween(12.0, 48.0);
    }

    @Test
    void profileValuesAreSaneOnRenderedText() {
        var rendered = TestImages.renderWord("Hello", "SansSerif", 30,
                Font.PLAIN, Color.BLACK, 2, 2);
        Word word = rendered.toWord(95);
        var profile = matcher.profile(word, rendered.image());

        assertThat(profile.aspectRatio()).isPositive();
        assertThat(profile.strokeRatio()).isBetween(0.0, 0.5);
        assertThat(profile.xHeightRatio()).isBetween(0.3, 1.0);
        assertThat(Double.isFinite(profile.italicSkew())).isTrue();
    }

    @Test
    void chosenFamilyIsAlwaysFromCandidates() {
        var rendered = TestImages.renderWord("test", "SansSerif", 20,
                Font.PLAIN, Color.BLACK, 2, 2);
        Word word = rendered.toWord(95);
        var profile = matcher.profile(word, rendered.image());
        assertThat(matcher.match(word, profile))
                .isIn(AwtFontMatcher.DEFAULT_CANDIDATES);
    }
}