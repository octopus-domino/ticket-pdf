package ru.ticket.recognition.style;

/** Иммутабельный стиль фрагмента текста, извлечённый из изображения. */
public record TextStyle(
    String color,
    double fontSize,
    boolean bold,
    boolean italic,
    boolean underline,
    boolean strike,
    String fontFamily,
    boolean reliable) {

  public TextStyle(
      String color,
      double fontSize,
      boolean bold,
      boolean italic,
      boolean underline,
      boolean strike,
      String fontFamily) {
    this(color, fontSize, bold, italic, underline, strike, fontFamily, true);
  }

  public static TextStyle defaultFor(String color, double fontSize) {
    return new TextStyle(color, fontSize, false, false, false, false, "Arial", false);
  }
}
