package ru.ticket.text.richtext;

/**
 * Run с вычисленными координатами после раскладки по строкам.
 * x, y, width — в локальной системе координат блока
 * (относительно верхнего-левого угла text-box с учётом padding).
 */
public record PositionedRun(
        String text,
        double x,
        double y,
        double width,
        int fontSize,
        String fontFamily,
        boolean bold,
        boolean italic,
        boolean underline,
        boolean strike,
        String color,
        String bgColor
) {}