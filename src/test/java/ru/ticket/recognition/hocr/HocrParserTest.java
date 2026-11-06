package ru.ticket.recognition.hocr;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ru.ticket.recognition.OcrEngine.StyleHints;
import ru.ticket.recognition.OcrEngine.Word;

class HocrParserTest {

    private final HocrParser parser = new HocrParser();

    @Test
    void parsesBasicWordWithBboxAndConfidence() {
        String hocr = """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml">
          <body>
            <div class='ocr_page'>
              <span class='ocr_line' id='line_1'>
                <span class='ocrx_word' title='bbox 10 20 50 40; x_wconf 96'>Hello</span>
              </span>
            </div>
          </body>
        </html>
        """;

        List<Word> words = parser.parse(hocr);

        assertThat(words).hasSize(1);
        Word w = words.get(0);
        assertThat(w.text()).isEqualTo("Hello");
        assertThat(w.x()).isEqualTo(10);
        assertThat(w.y()).isEqualTo(20);
        assertThat(w.width()).isEqualTo(40);
        assertThat(w.height()).isEqualTo(20);
        assertThat(w.confidence()).isEqualTo(96.0);
        assertThat(w.line()).isEqualTo("L0");
        assertThat(w.hints()).isEqualTo(StyleHints.NONE);
    }

    @Test
    void parsesExtendedStyleHints() {
        String hocr = """
        <html><body>
          <span class='ocr_line'>
            <span class='ocrx_word'
                  title='bbox 5 5 60 30; x_wconf 88; x_font Arial; x_fsize 14; x_bold 1; x_italic 0'>
              Hi
            </span>
          </span>
        </body></html>
        """;

        List<Word> words = parser.parse(hocr);

        assertThat(words).hasSize(1);
        StyleHints hints = words.get(0).hints();
        assertThat(hints.fontFamily()).isEqualTo("Arial");
        assertThat(hints.fontSize()).isEqualTo(14.0);
        assertThat(hints.bold()).isTrue();
        assertThat(hints.italic()).isFalse();
    }

    @Test
    void groupsWordsIntoLines() {
        String hocr = """
        <html><body>
          <span class='ocr_line'>
            <span class='ocrx_word' title='bbox 0 0 50 20; x_wconf 90'>Hello</span>
            <span class='ocrx_word' title='bbox 55 0 120 20; x_wconf 90'>world</span>
          </span>
          <span class='ocr_line'>
            <span class='ocrx_word' title='bbox 0 30 40 50; x_wconf 90'>Second</span>
          </span>
        </body></html>
        """;

        List<Word> words = parser.parse(hocr);

        assertThat(words).hasSize(3);
        assertThat(words.get(0).line()).isEqualTo(words.get(1).line());
        assertThat(words.get(1).line()).isNotEqualTo(words.get(2).line());
    }

    @Test
    void skipsWordsWithoutBbox() {
        String hocr = """
        <html><body>
          <span class='ocr_line'>
            <span class='ocrx_word' title='x_wconf 90'>no bbox</span>
            <span class='ocrx_word' title='bbox 0 0 10 10; x_wconf 90'>ok</span>
          </span>
        </body></html>
        """;

        List<Word> words = parser.parse(hocr);

        assertThat(words).hasSize(1);
        assertThat(words.get(0).text()).isEqualTo("ok");
    }

    @Test
    void handlesEmptyAndInvalidInput() {
        assertThat(parser.parse(null)).isEmpty();
        assertThat(parser.parse("")).isEmpty();
        assertThat(parser.parse("   ")).isEmpty();
        assertThat(parser.parse("not xml at all")).isEmpty();
    }

    @Test
    void malformedTitleFieldsAreIgnored() {
        String hocr = """
        <html><body>
          <span class='ocr_line'>
            <span class='ocrx_word' title='bbox abc def 10 10; x_wconf 90'>bad</span>
            <span class='ocrx_word' title='bbox 0 0 10 10; x_wconf 90'>ok</span>
          </span>
        </body></html>
        """;

        List<Word> words = parser.parse(hocr);

        assertThat(words).extracting(Word::text).containsExactly("ok");
    }
}