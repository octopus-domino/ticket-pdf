package ru.ticket.recognition;

import java.awt.image.BufferedImage;
import java.util.List;

public interface OcrEngine {
  record Word(String text, int x, int y, int width, int height, double confidence, String line) {}
  List<Word> recognize(BufferedImage image, String language);
}
