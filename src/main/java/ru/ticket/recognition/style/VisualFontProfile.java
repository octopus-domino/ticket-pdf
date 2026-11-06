package ru.ticket.recognition.style;

/** Визуальный «отпечаток» шрифта — признаки, видимые на скане. */
public record VisualFontProfile(
        double aspectRatio,
        double strokeRatio,
        double xHeightRatio,
        double italicSkew) {
}