package ru.ticket.recognition.style;

import static org.assertj.core.api.Assertions.assertThat;
import java.awt.Color;
import java.awt.Font;
import org.junit.jupiter.api.Test;
import ru.ticket.text.AwtTextLayoutService;

class GlyphTemplateMatcherTest {
    private final AwtFontMatcher matcher = new AwtFontMatcher(new AwtTextLayoutService());

    @Test void smallPrintedTextKeepsItsSizeWithoutTrustingHocrPointSizes() {
        var analyzer = new CompositeTextStyleAnalyzer(matcher);
        for (int size : new int[]{12, 14, 16, 18}) {
            var rendered = TestImages.renderWord("Recognition", "Arial", size, Font.PLAIN, Color.BLACK, 4, 4);
            var word = rendered.toWord(95);
            var style = analyzer.analyze(rendered.image(), word);
            assertThat(style.fontSize()).as("font size %s", size).isBetween(size - 3.0, size + 3.0);
            assertThat(style.fontFamily()).as("font family at %s", size).isEqualTo("Arial");
            assertThat(style.underline()).isFalse();
            assertThat(style.strike()).isFalse();
        }
    }

    @Test void matchesRenderedNormalBoldAndItalicGlyphsOnLightAndDarkPaper() {
        for (int style : new int[]{Font.PLAIN, Font.BOLD, Font.ITALIC, Font.BOLD | Font.ITALIC}) {
            var rendered = TestImages.renderWord("Recognition", "Arial", 32, style, Color.BLACK, 4, 4);
            var word = rendered.toWord(95);
            var profile = matcher.profile(word, rendered.image());
            var light = matcher.resolve(word, rendered.image(), profile);
            assertThat(light.family()).as("family for style %s", style).isEqualTo("Arial");
            assertThat(light.bold()).isEqualTo((style & Font.BOLD) != 0);
            assertThat(light.italic()).isEqualTo((style & Font.ITALIC) != 0);
            assertThat(light.size()).isBetween(29.0, 35.0);

            var image = rendered.image();
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
                image.setRGB(x, y, (~image.getRGB(x, y)) & 0xffffff);
            var darkProfile = matcher.profile(word, image);
            var dark = matcher.resolve(word, image, darkProfile);
            assertThat(dark).isEqualTo(light);
            assertThat(StrokeAnalyzer.hasUnderline(image, word.x(), word.y(), word.width(), word.height())).isFalse();
            assertThat(StrokeAnalyzer.hasStrikethrough(image, word.x(), word.y(), word.width(), word.height())).isFalse();
        }
    }

    @Test void clippedWordCoordinatesDoNotCrashStyleAnalysis() {
        var image = TestImages.blank(40, 20, Color.WHITE);
        assertThat(ForegroundMask.normalize(image, -5, -2, 60, 30).getWidth()).isEqualTo(60);
    }
}
