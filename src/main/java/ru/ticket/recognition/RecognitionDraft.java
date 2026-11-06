package ru.ticket.recognition;

import java.util.List;
import java.util.Map;

/**
 * Результат распознавания: плоский список элементов шаблона в z-порядке
 * (сначала фигуры, потом таблицы/текст/линии).
 *
 * <p>{@code Item} — минимальный дескриптор; конвертация в {@code Slide}
 * происходит в другом слое, чтобы парсер не зависел от модели шаблона.
 */
public record RecognitionDraft(
        int width,
        int height,
        List<Item> elements,
        List<String> warnings,
        String engine) {

  /**
   * @param type       {@code text|shape|line|table|image|stamp|chart|qr|path|footer}
   * @param properties параметры конкретного типа; ключи описаны в
   *                   {@code PageLayoutAnalyzer} и {@code ContourShapeRecognizer}
   */
  public record Item(
          String type,
          double x, double y,
          double width, double height,
          Map<String, Object> properties) {
  }
}