package ru.ticket.recognition;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Юнит-тест парсера TSV. Не требует установленного tesseract —
 * проверяет только разбор выходного формата.
 */
class TesseractOcrEngineParseTest {

    @Test
    void parsesValidWordRows() {
        String tsv = """
            5\t1\t1\t1\t1\t0\t10\t20\t50\t20\t92.5\tHello
            5\t1\t1\t1\t2\t0\t70\t20\t60\t20\t88.0\tworld
            """;
        var words = TesseractOcrEngine.parse(tsv);
        assertThat(words).hasSize(2);
        assertThat(words.get(0).text()).isEqualTo("Hello");
        assertThat(words.get(0).line()).isEqualTo("1:1:1:1");
        assertThat(words.get(1).text()).isEqualTo("world");
        assertThat(words.get(1).line()).isEqualTo("1:1:1:2");
    }

    @Test
    void skipsNonWordRows() {
        String tsv = """
            1\t1\t0\t0\t0\t0\t0\t0\t0\t0\t-1\t
            5\t1\t1\t1\t1\t0\t10\t20\t50\t20\t92.5\tHello
            """;
        assertThat(TesseractOcrEngine.parse(tsv)).hasSize(1);
    }

    @Test
    void skipsBlankAndMalformedRows() {
        String tsv = """

            5\t1\t1\t1\t1\t0\tabc\tdef\t10\t20\t92.5\tHello
            5\t1\t1\t1\t1\t0\t10\t20\t50\t20\t92.5\t
            """;
        assertThat(TesseractOcrEngine.parse(tsv)).isEmpty();
    }
}