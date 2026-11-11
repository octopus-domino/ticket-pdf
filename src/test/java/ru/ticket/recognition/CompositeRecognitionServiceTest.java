package ru.ticket.recognition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.ticket.recognition.ensemble.DualEngineOcrRunner;
import ru.ticket.recognition.ensemble.OcrResultFusion;
import ru.ticket.recognition.shape.ShapeRecognizer;

class CompositeRecognitionServiceTest {
    @Test void hocrOnlyResultIsNotDiscardedWhenTsvFails() {
        var image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        var dual = mock(DualEngineOcrRunner.class);
        var word = new OcrEngine.Word("Recovered", 10, 10, 80, 20, 95, "L1");
        when(dual.run(any(), anyString())).thenReturn(new DualEngineOcrRunner.DualResult(List.of(), List.of(word)));
        var analyzer = mock(PageLayoutAnalyzer.class);
        when(analyzer.analyze(any(), any(), anyBoolean())).thenAnswer(invocation -> {
            List<OcrEngine.Word> words = invocation.getArgument(1);
            assertThat(words).hasSize(1);
            assertThat(words.get(0).line()).isEqualTo("hocr:L1");
            return new RecognitionDraft(200, 100, List.of(), List.of(), "ocr");
        });
        var shapes = mock(ShapeRecognizer.class);
        when(shapes.recognize(any(), any())).thenReturn(List.of());
        var result = new CompositeRecognitionService(dual, new OcrResultFusion(), analyzer, shapes).recognize(image, "eng");
        assertThat(result.warnings()).doesNotContain("recognition.empty_result");
    }

    @Test
    void dualEngineAndShapesAreMergedIntoOneDraft() {
        BufferedImage image = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);

        var dual = mock(DualEngineOcrRunner.class);
        when(dual.run(any(), anyString())).thenReturn(
                new DualEngineOcrRunner.DualResult(
                        List.of(new OcrEngine.Word("Hello", 10, 10, 60, 20, 90, "L1")),
                        List.of(new OcrEngine.Word("Hello", 11, 10, 60, 20, 92, "L1"))));

        var analyzer = mock(PageLayoutAnalyzer.class);
        when(analyzer.analyze(any(), any(), anyBoolean())).thenReturn(
                new RecognitionDraft(300, 200,
                        List.of(new RecognitionDraft.Item("text", 10, 10, 60, 20,
                                Map.of("text", "Hello", "richText", false))),
                        List.of(), "ocr"));

        var shapes = mock(ShapeRecognizer.class);
        when(shapes.recognize(any(), any())).thenReturn(
                List.of(new RecognitionDraft.Item("shape", 100, 100, 50, 50,
                        Map.of("shape", "rect", "fill", "#ff0000"))));

        var service = new CompositeRecognitionService(
                dual, new OcrResultFusion(), analyzer, shapes);
        var draft = service.recognize(image, "eng");

        assertThat(draft.engine()).isEqualTo("dual");
        assertThat(draft.elements()).anyMatch(i -> "text".equals(i.type()));
        assertThat(draft.elements()).anyMatch(i -> "shape".equals(i.type()));
        // Фигуры идут раньше текста — рендерятся под ним.
        assertThat(draft.elements().get(0).type()).isEqualTo("shape");
    }

    @Test
    void engineDisagreementIsReportedAsWarning() {
        BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);

        var dual = mock(DualEngineOcrRunner.class);
        when(dual.run(any(), anyString())).thenReturn(
                new DualEngineOcrRunner.DualResult(
                        List.of(new OcrEngine.Word("Helo", 10, 10, 60, 20, 70, "L1")),
                        List.of(new OcrEngine.Word("Hello", 10, 10, 60, 20, 95, "L1"))));

        var analyzer = mock(PageLayoutAnalyzer.class);
        when(analyzer.analyze(any(), any(), anyBoolean()))
                .thenReturn(new RecognitionDraft(200, 100, List.of(), List.of(), "ocr"));

        var shapes = mock(ShapeRecognizer.class);
        when(shapes.recognize(any(), any())).thenReturn(List.of());

        var service = new CompositeRecognitionService(
                dual, new OcrResultFusion(), analyzer, shapes);
        var draft = service.recognize(image, "eng");

        assertThat(draft.warnings()).contains("recognition.engine_disagreement");
    }

    @Test
    void bothEnginesEmptyAddsEmptyResultWarning() {
        BufferedImage image = new BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB);

        var dual = mock(DualEngineOcrRunner.class);
        when(dual.run(any(), anyString()))
                .thenReturn(new DualEngineOcrRunner.DualResult(List.of(), List.of()));

        var analyzer = mock(PageLayoutAnalyzer.class);
        when(analyzer.analyze(any(), any(), anyBoolean()))
                .thenReturn(new RecognitionDraft(100, 50, List.of(), List.of(), "ocr"));

        var shapes = mock(ShapeRecognizer.class);
        when(shapes.recognize(any(), any())).thenReturn(List.of());

        var draft = new CompositeRecognitionService(
                dual, new OcrResultFusion(), analyzer, shapes).recognize(image, "eng");

        assertThat(draft.warnings()).contains("recognition.empty_result");
    }
}
