package ru.ticket.recognition.ensemble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.ticket.recognition.OcrEngine.Word;
import ru.ticket.recognition.TesseractOcrEngine;
import ru.ticket.recognition.hocr.TesseractHocrEngine;

class DualEngineOcrRunnerTest {

    private static final BufferedImage IMAGE =
            new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);

    @Test
    void runsBothEnginesAndReturnsResults() {
        var tsvEngine = mock(TesseractOcrEngine.class);
        var hocrEngine = mock(TesseractHocrEngine.class);
        when(tsvEngine.recognize(any(), anyString()))
                .thenReturn(List.of(new Word("Hello", 0, 0, 10, 10, 90, "L1")));
        when(hocrEngine.recognize(any(), anyString()))
                .thenReturn(List.of(new Word("Hello", 0, 0, 10, 10, 92, "L1")));

        var runner = new DualEngineOcrRunner(tsvEngine, hocrEngine);
        var result = runner.run(IMAGE, "eng");

        assertThat(result.tsvWords()).hasSize(1);
        assertThat(result.hocrWords()).hasSize(1);
    }

    @Test
    void oneEngineFailureYieldsEmptyListForThatEngine() {
        var tsvEngine = mock(TesseractOcrEngine.class);
        var hocrEngine = mock(TesseractHocrEngine.class);
        when(tsvEngine.recognize(any(), anyString()))
                .thenThrow(new RuntimeException("boom"));
        when(hocrEngine.recognize(any(), anyString()))
                .thenReturn(List.of(new Word("Hello", 0, 0, 10, 10, 92, "L1")));

        var runner = new DualEngineOcrRunner(tsvEngine, hocrEngine);
        var result = runner.run(IMAGE, "eng");

        assertThat(result.tsvWords()).isEmpty();
        assertThat(result.hocrWords()).hasSize(1);
    }

    @Test
    void bothEnginesFailingYieldsBothEmpty() {
        var tsvEngine = mock(TesseractOcrEngine.class);
        var hocrEngine = mock(TesseractHocrEngine.class);
        when(tsvEngine.recognize(any(), anyString()))
                .thenThrow(new RuntimeException("boom-tsv"));
        when(hocrEngine.recognize(any(), anyString()))
                .thenThrow(new RuntimeException("boom-hocr"));

        var runner = new DualEngineOcrRunner(tsvEngine, hocrEngine);
        var result = runner.run(IMAGE, "eng");

        assertThat(result.tsvWords()).isEmpty();
        assertThat(result.hocrWords()).isEmpty();
    }
}