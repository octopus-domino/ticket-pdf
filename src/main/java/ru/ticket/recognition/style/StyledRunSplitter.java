package ru.ticket.recognition.style;

import java.awt.image.BufferedImage;
import java.util.List;
import ru.ticket.recognition.OcrEngine.Word;

/** Разбивает строку OCR на смежные фрагменты с одинаковым стилем. */
public interface StyledRunSplitter {
    List<StyledRun> split(BufferedImage image, List<Word> line);
}