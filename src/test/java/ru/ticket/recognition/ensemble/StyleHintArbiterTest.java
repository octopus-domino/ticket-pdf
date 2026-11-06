package ru.ticket.recognition.ensemble;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ru.ticket.recognition.OcrEngine.StyleHints;

class StyleHintArbiterTest {

    private final StyleHintArbiter arbiter = new StyleHintArbiter();

    @Test
    void nullHintsAreRejected() {
        assertThat(arbiter.arbiter(null)).isEqualTo(StyleHints.NONE);
        assertThat(arbiter.arbiter(StyleHints.NONE)).isEqualTo(StyleHints.NONE);
    }

    @Test
    void incompleteHintsFallBackToNone() {
        var partial = new StyleHints("Arial", null, true, null);
        assertThat(arbiter.arbiter(partial)).isEqualTo(StyleHints.NONE);
    }

    @Test
    void completeHintsPassThrough() {
        var hints = new StyleHints("Georgia", 14.0, true, false);
        assertThat(arbiter.arbiter(hints)).isEqualTo(hints);
    }

    @Test
    void blankFontFamilyIsRejected() {
        var hints = new StyleHints("   ", 14.0, true, false);
        assertThat(arbiter.arbiter(hints)).isEqualTo(StyleHints.NONE);
    }
}