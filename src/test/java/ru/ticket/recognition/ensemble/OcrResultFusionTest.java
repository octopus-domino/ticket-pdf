package ru.ticket.recognition.ensemble;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ru.ticket.recognition.OcrEngine.StyleHints;
import ru.ticket.recognition.OcrEngine.Word;

class OcrResultFusionTest {

    private final OcrResultFusion fusion = new OcrResultFusion();

    @Test
    void identicalWordsMergeAndKeepConfidence() {
        Word a = new Word("Hello", 10, 10, 50, 20, 90, "L1");
        Word b = new Word("Hello", 11, 10, 50, 20, 92, "L1");

        var fused = fusion.fuse(List.of(a), List.of(b));

        assertThat(fused).hasSize(1);
        assertThat(fused.get(0).sources()).isEqualTo(2);
        assertThat(fused.get(0).disagreement()).isFalse();
        assertThat(fused.get(0).confidence()).isGreaterThan(90);
    }

    @Test
    void hocrHintsAreCarriedThrough() {
        Word tsv = new Word("Total", 10, 10, 60, 20, 88, "L1");
        Word hocr = new Word("Total", 10, 10, 60, 20, 90, "L1",
                new StyleHints("Georgia", 16.0, true, false));

        var fused = fusion.fuse(List.of(tsv), List.of(hocr));

        assertThat(fused).hasSize(1);
        assertThat(fused.get(0).hints().fontFamily()).isEqualTo("Georgia");
        assertThat(fused.get(0).hints().fontSize()).isEqualTo(16.0);
        assertThat(fused.get(0).hints().bold()).isTrue();
    }

    @Test
    void differingTextIsFlaggedForReview() {
        Word a = new Word("Helo", 10, 10, 50, 20, 70, "L1");
        Word b = new Word("Hello", 11, 10, 50, 20, 95, "L1");

        var fused = fusion.fuse(List.of(a), List.of(b));

        assertThat(fused).hasSize(1);
        assertThat(fused.get(0).text()).isEqualTo("Hello");
        assertThat(fused.get(0).disagreement()).isTrue();
    }

    @Test
    void nonOverlappingWordsStaySeparate() {
        Word a = new Word("Left", 0, 0, 40, 20, 90, "L1");
        Word b = new Word("Right", 200, 0, 40, 20, 90, "L1");

        var fused = fusion.fuse(List.of(a), List.of(b));

        assertThat(fused).hasSize(2);
        assertThat(fused).allMatch(w -> w.sources() == 1);
    }

    @Test
    void iouBelowThresholdDoesNotMerge() {
        Word a = new Word("A", 0, 0, 40, 20, 90, "L1");
        Word b = new Word("B", 30, 0, 40, 20, 90, "L1");
        assertThat(OcrResultFusion.iou(a, b)).isLessThan(OcrResultFusion.IOU_THRESHOLD);

        var fused = fusion.fuse(List.of(a), List.of(b));
        assertThat(fused).hasSize(2);
    }

    @Test
    void emptyInputsProduceEmptyOutput() {
        assertThat(fusion.fuse(List.of(), List.of())).isEmpty();
        assertThat(fusion.fuse(List.of(),
                List.of(new Word("x", 0, 0, 10, 10, 90, "L"))))
                .hasSize(1);
    }
}