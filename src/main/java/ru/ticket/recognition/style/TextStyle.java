package ru.ticket.recognition.style;

/** Иммутабельный стиль фрагмента текста, извлечённый из изображения. */
public record TextStyle(
        String color,
        double fontSize,
        boolean bold,
        boolean italic,
        boolean underline,
        boolean strike,
        String fontFamily) {

    public static TextStyle defaultFor(String color, double fontSize) {
        return new TextStyle(color, fontSize, false, false, false, false, "Arial");
    }
}