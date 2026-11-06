package ru.ticket.recognition.hocr;

import java.awt.image.BufferedImage;
import java.util.List;
import org.springframework.stereotype.Component;
import ru.ticket.recognition.OcrEngine;
import ru.ticket.recognition.tesseract.TesseractProcessRunner;

/**
 * hOCR pass over Tesseract.
 *
 * <p><b>No {@code hocr_font_info}.</b> Tesseract's font hints are noisy:
 * they frequently report wrong sizes and bold/italic states. When present, they
 * override the far more accurate pixel-based {@code CompositeTextStyleAnalyzer}.
 * Omitting them keeps the pre-fusion style quality: font family, weight, italic
 * and colour are inferred directly from the glyph bitmaps.
 */
@Component
public class TesseractHocrEngine implements OcrEngine {

    private static final long MAX_OUTPUT_BYTES = 4_000_000L;

    private final TesseractProcessRunner runner;
    private final HocrParser parser;

    public TesseractHocrEngine(TesseractProcessRunner runner, HocrParser parser) {
        this.runner = runner;
        this.parser = parser;
    }

    @Override
    public List<Word> recognize(BufferedImage image, String language) {
        var request = new TesseractProcessRunner.Request(
                List.of("-l", language, "--psm", "3", "hocr"),
                MAX_OUTPUT_BYTES);
        return parser.parse(runner.run(image, request));
    }
}