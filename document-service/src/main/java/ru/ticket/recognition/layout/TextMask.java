package ru.ticket.recognition.layout;

import java.util.List;
import ru.ticket.recognition.OcrEngine.Word;
import ru.ticket.recognition.RecognitionDraft.Item;

/**
 * Плоская маска {@code boolean[width * height]}, помечающая пиксели,
 * «занятые» уже размеченными элементами.
 *
 * <p>Используется двумя потребителями: {@code PageLayoutAnalyzer} (маскирует
 * слова, таблицы, линии перед artwork-эвристикой) и
 * {@code CompositeRecognitionService} (маскирует текст перед shape-детектором).
 * Одна логика — один класс (DRY + SRP).
 */
public final class TextMask {

    private TextMask() {
    }

    /** Заливает прямоугольник в маску, обрезая по границам. */
    public static void paint(
            boolean[] mask, int imageWidth, int imageHeight,
            int x, int y, int width, int height) {

        int rowStart = Math.max(0, y);
        int rowEnd = Math.min(imageHeight, y + height);
        int colStart = Math.max(0, x);
        int colEnd = Math.min(imageWidth, x + width);

        for (int row = rowStart; row < rowEnd; row++) {
            int offset = row * imageWidth;
            for (int col = colStart; col < colEnd; col++) {
                mask[offset + col] = true;
            }
        }
    }

    /** Маска из OCR-слов с историческим паддингом ±2/4. */
    public static boolean[] fromOcrWords(int width, int height, List<Word> words) {
        boolean[] mask = new boolean[width * height];
        for (Word word : words) {
            paint(mask, width, height,
                    word.x() - 2, word.y() - 2,
                    word.width() + 4, word.height() + 4);
        }
        return mask;
    }

    /**
     * Маска из уже размеченных элементов. Shape/image пропускаются —
     * они не должны маскироваться на повторном проходе shape-детектора.
     */
    public static boolean[] fromItems(int width, int height, List<Item> items) {
        boolean[] mask = new boolean[width * height];
        for (Item item : items) {
            if ("shape".equals(item.type()) || "image".equals(item.type())) continue;

            int startX = (int) Math.floor(item.x());
            int startY = (int) Math.floor(item.y());
            int endX = (int) Math.ceil(item.x() + item.width());
            int endY = (int) Math.ceil(item.y() + item.height());

            paint(mask, width, height,
                    startX, startY, endX - startX, endY - startY);
        }
        return mask;
    }
}