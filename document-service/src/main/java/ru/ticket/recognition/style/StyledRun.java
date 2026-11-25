package ru.ticket.recognition.style;

import ru.ticket.recognition.OcrEngine.Word;

/**
 * Пара (слово, стиль) — единица rich-разметки строки. {@code word.text()}
 * может содержать склейку соседних слов одного стиля (с пробелом).
 */
public record StyledRun(Word word, TextStyle style) {
}