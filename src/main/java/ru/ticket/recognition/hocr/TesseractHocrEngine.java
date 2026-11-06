package ru.ticket.recognition.hocr;

import java.awt.image.BufferedImage;
import java.util.List;
import org.springframework.stereotype.Component;
import ru.ticket.recognition.OcrEngine;
import ru.ticket.recognition.tesseract.TesseractProcessRunner;

/**
 * hOCR-проход Tesseract с расширенными подсказками шрифта.
 *
 * <p><b>Роль.</b> При {@code -c hocr_font_info=1} Tesseract пытается отдать
 * {@code x_font, x_fsize, x_bold, x_italic}. Подсказки ненадёжны, но когда
 * приходят — это осознанное подтверждение движка, и
 * {@code CompositeTextStyleAnalyzer} использует их с приоритетом.
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
                List.of("-l", language, "--psm", "3", "hocr",
                        "-c", "hocr_font_info=1"),
                MAX_OUTPUT_BYTES);
        return parser.parse(runner.run(image, request));
    }
}