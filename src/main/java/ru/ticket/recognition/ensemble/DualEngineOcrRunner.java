package ru.ticket.recognition.ensemble;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Component;
import ru.ticket.recognition.OcrEngine.Word;
import ru.ticket.recognition.TesseractOcrEngine;
import ru.ticket.recognition.hocr.TesseractHocrEngine;

/**
 * Запускает TSV- и hOCR-проходы Tesseract параллельно на одном изображении.
 * Ни один не «главный»: оба результата передаются в {@link OcrResultFusion}.
 */
@Component
public class DualEngineOcrRunner {

    private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "ocr-dual");
        t.setDaemon(true);
        return t;
    });

    private final TesseractOcrEngine tsvEngine;
    private final TesseractHocrEngine hocrEngine;

    public DualEngineOcrRunner(
            TesseractOcrEngine tsvEngine, TesseractHocrEngine hocrEngine) {
        this.tsvEngine = tsvEngine;
        this.hocrEngine = hocrEngine;
    }

    public record DualResult(List<Word> tsvWords, List<Word> hocrWords) {
    }

    public DualResult run(BufferedImage image, String language) {
        CompletableFuture<List<Word>> tsvFuture =
                CompletableFuture.supplyAsync(() -> tsvEngine.recognize(image, language), pool);
        CompletableFuture<List<Word>> hocrFuture =
                CompletableFuture.supplyAsync(() -> hocrEngine.recognize(image, language), pool);

        List<Word> tsv;
        List<Word> hocr;
        try {
            tsv = tsvFuture.join();
        } catch (RuntimeException error) {
            tsv = List.of();
        }
        try {
            hocr = hocrFuture.join();
        } catch (RuntimeException error) {
            hocr = List.of();
        }
        return new DualResult(tsv, hocr);
    }
}