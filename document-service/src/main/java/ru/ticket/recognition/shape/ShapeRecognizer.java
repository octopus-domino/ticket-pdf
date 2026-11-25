package ru.ticket.recognition.shape;

import java.awt.image.BufferedImage;
import java.util.List;
import ru.ticket.recognition.RecognitionDraft.Item;

/** Порт распознавания фигур на растре с замаскированным текстом. */
public interface ShapeRecognizer {
    List<Item> recognize(BufferedImage image, boolean[] textMask);
}