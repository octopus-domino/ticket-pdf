package ru.ticket.recognition;

import java.util.List;
import java.util.Map;

public record RecognitionDraft(int width, int height, List<Item> elements, List<String> warnings, String engine) {
  public record Item(String type, double x, double y, double width, double height, Map<String, Object> properties) {}
}
