package ru.ticket.recognition;

import static org.assertj.core.api.Assertions.assertThat;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import ru.ticket.recognition.OcrEngine.Word;
import ru.ticket.recognition.ensemble.*;
import ru.ticket.recognition.layout.*;
import ru.ticket.recognition.style.StableLineRunSplitter;

class ImageRecognitionRegressionTest {
    @Test void sparsePassCannotDuplicateExistingInkButKeepsMissingLabels() {
        var primary = new Word("First", 10, 10, 50, 20, 95, "line");
        var duplicate = new Word("First", 12, 8, 48, 15, 90, "sparse");
        var label = new Word("Label", 100, 10, 40, 20, 90, "sparse2");
        var result = OcrLineReconciler.reconcile(new OcrResultFusion().fuse(List.of(primary), List.of(duplicate, label)), List.of(primary));
        assertThat(result).extracting(Word::text).containsExactlyInAnyOrder("First", "Label");
    }

    @Test void smallImageIsEnlargedOnlyForOcrAndCoordinatesReturnToSource() {
        var source = new BufferedImage(600, 800, BufferedImage.TYPE_INT_RGB);
        source.setRGB(10, 10, Color.RED.getRGB());
        try (var prepared = new OcrRasterPreparation(source)) {
            assertThat(prepared.image().getWidth()).isEqualTo(1800);
            var restored = prepared.restore(List.of(new Word("Text", 30, 60, 120, 30, 95, "L"))).get(0);
            assertThat(restored.x()).isEqualTo(10);
            assertThat(restored.height()).isEqualTo(10);
            assertThat(source.getRGB(10, 10)).isEqualTo(Color.RED.getRGB());
        }
    }

    @Test void palePrintedGridBecomesOneEditableTable() {
        var image = new BufferedImage(600, 500, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0, 0, 600, 500);
        g.setColor(new Color(220, 220, 220));
        for (int x : new int[]{20, 220, 420, 580}) g.drawLine(x, 100, x, 450);
        for (int y : new int[]{100, 180, 260, 340, 450}) g.drawLine(20, y, 580, y);
        g.dispose();
        var tables = new RuledTableDetector().detect(image, List.of(), PaleGridRules.detect(image, false), PaleGridRules.detect(image, true));
        assertThat(tables.tables()).hasSize(1);
        assertThat(tables.tables().get(0).properties().get("cols")).isEqualTo(3);
    }

    /** Local screenshot stays outside Git; only geometry statistics are printed. */
    @Test void inspectSuppliedScreenshotWhenProvided() throws Exception {
        String path = System.getProperty("recognition.imageSample");
        org.junit.jupiter.api.Assumptions.assumeTrue(path != null);
        var image = ImageIO.read(Path.of(path).toFile());
        var horizontal = PaleGridRules.detect(image, false);
        var vertical = PaleGridRules.detect(image, true);
        System.out.println("pale-grid horizontal=" + horizontal + " vertical=" + vertical);
        var analyzer = new PageLayoutAnalyzer(new RuledTableDetector(), new TextElementBuilder(new StableLineRunSplitter()), new ArtworkDetector());
        var draft = analyzer.analyze(image, List.of(), false);
        var tables = draft.elements().stream().filter(item -> item.type().equals("table")).toList();
        System.out.println("screenshot tables=" + tables.size());
        assertThat(tables).isNotEmpty();
        new com.fasterxml.jackson.databind.ObjectMapper().writeValue(Path.of(System.getProperty("recognition.imageOutput")).toFile(), draft);
    }
}
