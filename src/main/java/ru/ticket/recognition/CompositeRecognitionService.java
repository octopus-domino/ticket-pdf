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
 * Top-level recognition: dual-pass OCR → fusion → shape recognition → merge.
 *
 * <p><b>Contract.</b> Fusion produces a {@link FusedWord} stream whose
 * {@code line} field is taken from TSV, so {@link #recognize} feeds
 * {@link PageLayoutAnalyzer} a word list where every word already carries the
 * correct visual-line id. The layout analyzer then groups by {@code line} and
 * produces one text element per visual line — exactly the plain-Tesseract
 * behaviour, with per-run style enrichment added on top.
 *
 * <p>The single-element "everything is one line" regression stemmed from
 * {@code FusedWord.toWord("fused")} being used unconditionally. The fusion
 * record now preserves the line id, so passing the literal {@code "fused"}
 * here is a *fallback* only (never reached in practice). {@link FusedWord}
 * additionally buckets by baseline when line is missing so even a badly
 * behaved upstream stage cannot collapse the page.
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
        // 1. Dual-pass OCR + fusion. TSV line ids are preserved end-to-end.
        var dual = dualRunner.run(image, language);
        var fused = fusion.fuse(dual.tsvWords(), dual.hocrWords());
        var words = fused.stream()
                .map(f -> f.toWord("fused"))
                .toList();

        // 2. Layout without artwork first: tables, text, rules.
        var baseDraft = analyzer.analyze(image, words, false);

        // 3. Find shapes that were not covered by text/table/line masks.
        var mask = buildMask(image, baseDraft.elements());
        var shapeItems = shapeRecognizer.recognize(image, mask);

        // 4. Merge: artwork first (bottom), then structural elements.
        var merged = new ArrayList<Item>(shapeItems.size() + baseDraft.elements().size());
        merged.addAll(shapeItems);
        merged.addAll(baseDraft.elements());

        // 5. Warnings.
        var warnings = new ArrayList<>(baseDraft.warnings());
        if (fused.stream().anyMatch(FusedWord::disagreement)) {
            warnings.add("recognition.engine_disagreement");
        }
        if (dual.tsvWords().isEmpty()) {
            // TSV is authoritative; empty TSV means no reliable text at all.
            warnings.add("recognition.empty_result");
        }

        return new RecognitionDraft(
                image.getWidth(), image.getHeight(), merged, warnings, "dual");
    }

    private static boolean[] buildMask(BufferedImage image, List<Item> items) {
        int w = image.getWidth(), h = image.getHeight();
        boolean[] mask = new boolean[w * h];
        for (Item item : items) {
            if ("shape".equals(item.type()) || "image".equals(item.type())) continue;
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