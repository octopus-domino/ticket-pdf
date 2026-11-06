package ru.ticket.recognition;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.ticket.recognition.OcrEngine.Word;
import ru.ticket.recognition.layout.ArtworkDetector;
import ru.ticket.recognition.layout.RuledTableDetector;
import ru.ticket.recognition.layout.TextElementBuilder;
import ru.ticket.recognition.style.StyledRun;
import ru.ticket.recognition.style.StyledRunSplitter;
import ru.ticket.recognition.style.TextStyle;

/**
 * Интеграционный тест оркестратора: реальные коллабораторы + маленькое
 * изображение. Не проверяет качество распознавания — только что
 * оркестратор корректно раздаёт работу и собирает элементы.
 */
class PageLayoutAnalyzerTest {

    /** Стабильный splitter: одно слово — один run. */
    private static final StyledRunSplitter FIXED_SPLITTER = (image, line) ->
            line.stream()
                    .map(w -> new StyledRun(w,
                            new TextStyle("#000000", 16, false, false, false, false, "Arial")))
                    .toList();

    private final PageLayoutAnalyzer analyzer = new PageLayoutAnalyzer(
            new RuledTableDetector(),
            new TextElementBuilder(FIXED_SPLITTER),
            new ArtworkDetector());

    @Test
    void textWordsBecomeTextItem() {
        BufferedImage image = blank(200, 50);
        Word word = new Word("Hello world", 10, 10, 100, 20, 95, "L1");

        var draft = analyzer.analyze(image, List.of(word));

        assertThat(draft.elements()).anyMatch(i -> "text".equals(i.type()));
    }

    @Test
    void artworkIsSkippedWhenDisabled() {
        BufferedImage image = blank(200, 200);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.BLACK);
            g.fillRect(40, 40, 120, 120);
        } finally {
            g.dispose();
        }

        var withArtwork = analyzer.analyze(image, List.of(), true);
        var withoutArtwork = analyzer.analyze(image, List.of(), false);

        assertThat(withArtwork.elements()).isNotEmpty();
        assertThat(withoutArtwork.elements()).isEmpty();
    }

    @Test
    void emptyPageEmitsNoTextAndReviewWarnings() {
        var draft = analyzer.analyze(blank(100, 50), List.of());
        assertThat(draft.warnings()).contains("recognition.review_required");
        assertThat(draft.warnings()).contains("recognition.no_text");
    }

    private static BufferedImage blank(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
        } finally {
            g.dispose();
        }
        return img;
    }
}