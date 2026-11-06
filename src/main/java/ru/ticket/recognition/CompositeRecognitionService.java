package ru.ticket.recognition;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import ru.ticket.recognition.RecognitionDraft.Item;
import ru.ticket.recognition.ensemble.DualEngineOcrRunner;
import ru.ticket.recognition.ensemble.FusedWord;
import ru.ticket.recognition.ensemble.OcrResultFusion;
import ru.ticket.recognition.shape.ShapeRecognizer;

/**
 * Top-level recognition: dual-pass OCR → fusion → layout (with artwork) → shapes.
 *
 * <p><b>ArtworkDetector is enabled.</b> It rasterizes complex graphics
 * (diagonal card designs, coloured panels, gradients) into image crops,
 * preserving visual fidelity of the original. The mask fed to
 * {@link ShapeRecognizer} includes every element produced by the layout stage,
 * so contour recognition cannot duplicate artwork pixels.
 */
@Service
public class CompositeRecognitionService {

    private final DualEngineOcrRunner dualRunner;
    private final OcrResultFusion fusion;
    private final PageLayoutAnalyzer analyzer;
    private final ShapeRecognizer shapeRecognizer;

    public CompositeRecognitionService(
            DualEngineOcrRunner dualRunner,
            OcrResultFusion fusion,
            PageLayoutAnalyzer analyzer,
            ShapeRecognizer shapeRecognizer) {
        this.dualRunner = dualRunner;
        this.fusion = fusion;
        this.analyzer = analyzer;
        this.shapeRecognizer = shapeRecognizer;
    }

    public RecognitionDraft recognize(BufferedImage image, String language) {
        var dual = dualRunner.run(image, language);
        var fused = fusion.fuse(dual.tsvWords(), dual.hocrWords());
        var words = fused.stream()
                .map(f -> f.toWord("fused"))
                .toList();

        // Layout WITH artwork: complex graphics become image crops.
        var baseDraft = analyzer.analyze(image, words, true);

        // Mask every element the layout produced. Contour recognition then
        // only sees genuinely uncovered pixels — no duplicates of artwork.
        var mask = buildMask(image, baseDraft.elements());
        var shapeItems = shapeRecognizer.recognize(image, mask);

        var merged = new ArrayList<Item>(shapeItems.size() + baseDraft.elements().size());
        merged.addAll(shapeItems);
        merged.addAll(baseDraft.elements());

        var warnings = new ArrayList<>(baseDraft.warnings());
        if (fused.stream().anyMatch(FusedWord::disagreement)) {
            warnings.add("recognition.engine_disagreement");
        }
        if (dual.tsvWords().isEmpty()) {
            warnings.add("recognition.empty_result");
        }

        return new RecognitionDraft(
                image.getWidth(), image.getHeight(), merged, warnings, "dual");
    }

    private static boolean[] buildMask(BufferedImage image, List<Item> items) {
        int w = image.getWidth(), h = image.getHeight();
        boolean[] mask = new boolean[w * h];
        for (Item item : items) {
            int x = (int) Math.floor(item.x());
            int y = (int) Math.floor(item.y());
            int right  = (int) Math.ceil(item.x() + item.width());
            int bottom = (int) Math.ceil(item.y() + item.height());
            for (int row = Math.max(0, y); row < Math.min(h, bottom); row++) {
                for (int col = Math.max(0, x); col < Math.min(w, right); col++) {
                    mask[row * w + col] = true;
                }
            }
        }
        return mask;
    }
}