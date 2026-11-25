package ru.ticket.recognition.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.ticket.recognition.OcrEngine.Word;
import ru.ticket.recognition.RecognitionDraft.Item;

class TextMaskTest {

    @Test
    void paintMarksRectangle() {
        boolean[] mask = new boolean[10 * 10];
        TextMask.paint(mask, 10, 10, 2, 2, 3, 3);
        assertThat(mask[2 * 10 + 2]).isTrue();
        assertThat(mask[4 * 10 + 4]).isTrue();
        assertThat(mask[0]).isFalse();
        assertThat(mask[5 * 10 + 5]).isFalse();
    }

    @Test
    void paintClipsToBounds() {
        boolean[] mask = new boolean[10 * 10];
        TextMask.paint(mask, 10, 10, -5, -5, 10, 10);
        assertThat(mask[0]).isTrue();
        assertThat(mask[4 * 10 + 4]).isTrue();
        assertThat(mask[5 * 10 + 5]).isFalse();
    }

    @Test
    void fromOcrWordsAppliesPadding() {
        boolean[] mask = TextMask.fromOcrWords(100, 100,
                List.of(new Word("X", 10, 10, 5, 5, 90, "L1")));

        // Слово: x=10, y=10, w=5, h=5.
        // С паддингом: x=8, y=8, w=9, h=9.
        // Значит, маска покрывает прямоугольник от 8 до 16 включительно.

        assertThat(mask[8 * 100 + 8]).isTrue();   // Левый верхний угол
        assertThat(mask[16 * 100 + 16]).isTrue(); // Правый нижний угол
        assertThat(mask[17 * 100 + 17]).isFalse(); // За пределами
        assertThat(mask[7 * 100 + 7]).isFalse();   // До паддинга
    }

    @Test
    void fromItemsSkipsShapesAndImages() {
        List<Item> items = List.of(
                new Item("text", 10, 10, 20, 20, Map.of()),
                new Item("shape", 50, 50, 20, 20, Map.of()),
                new Item("image", 80, 80, 20, 20, Map.of())
        );
        boolean[] mask = TextMask.fromItems(200, 200, items);
        assertThat(mask[15 * 200 + 15]).isTrue();
        assertThat(mask[55 * 200 + 55]).isFalse();
        assertThat(mask[85 * 200 + 85]).isFalse();
    }
}