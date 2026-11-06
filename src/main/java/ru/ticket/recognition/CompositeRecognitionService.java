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
 * Оркестратор распознавания: dual-pass OCR → fusion → полный layout без
 * artwork → точные векторные/растровые фигуры → merge.
 *
 * <p>Переиспользует {@link PageLayoutAnalyzer} для всего, что уже работает:
 * таблицы, линии, текстовые элементы, маски. Отличие от старого пути — на
 * входе идут {@link FusedWord} (а не один OCR-проход), а artwork-детектор
 * заменён на {@link ShapeRecognizer} с точной классификацией.
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
        // 1. Оба OCR-прохода параллельно + fusion.
        var dual = dualRunner.run(image, language);
        var fused = fusion.fuse(dual.tsvWords(), dual.hocrWords());
        var words = fused.stream()
                .map(f -> f.toWord("fused"))
                .toList();

        // 2. Полный layout без artwork: текст, таблицы, линии.
        var baseDraft = analyzer.analyze(image, words, false);

        // 3. Точные фигуры/кропы на всём, что не занято текстом/таблицами/линиями.
        var mask = buildMask(image, baseDraft.elements());
        var shapeItems = shapeRecognizer.recognize(image, mask);

        // 4. Merge: фигуры — под текст, чтобы текст был сверху.
        var merged = new ArrayList<Item>(shapeItems.size() + baseDraft.elements().size());
        merged.addAll(shapeItems);
        merged.addAll(baseDraft.elements());

        // 5. Диагностика: если движки разошлись или оба пусты — сигнал наружу.
        var warnings = new ArrayList<>(baseDraft.warnings());
        if (fused.stream().anyMatch(FusedWord::disagreement)) {
            warnings.add("recognition.engine_disagreement");
        }
        if (dual.tsvWords().isEmpty() && dual.hocrWords().isEmpty()) {
            warnings.add("recognition.empty_result");
        }

        return new RecognitionDraft(
                image.getWidth(), image.getHeight(), merged, warnings, "dual");
    }

    /**
     * Маскирует всё, что уже размечено как текст/таблица/линия.
     * ShapeRecognition получает чистый остаток и не путает буквы с фигурами.
     */
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