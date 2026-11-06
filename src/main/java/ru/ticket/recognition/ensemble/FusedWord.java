package ru.ticket.recognition.ensemble;

import ru.ticket.recognition.OcrEngine.StyleHints;
import ru.ticket.recognition.OcrEngine.Word;

/**
 * Результат слияния нескольких OCR-проходов для одного визуального слова.
 *
 * @param sources     сколько движков подтвердили слово (1 или 2)
 * @param hints       подсказки hOCR (если есть) — пробрасываются в стилевой анализ
 * @param disagreement {@code true}, если TSV и hOCR дали разный текст;
 *                     выбран более уверенный, но помечено для ревью
 */
public record FusedWord(
        String text,
        int x, int y, int width, int height,
        double confidence,
        int sources,
        StyleHints hints,
        boolean disagreement) {

    public Word toWord(String lineId) {
        return new Word(text, x, y, width, height, confidence, lineId,
                hints == null ? StyleHints.NONE : hints);
    }
}