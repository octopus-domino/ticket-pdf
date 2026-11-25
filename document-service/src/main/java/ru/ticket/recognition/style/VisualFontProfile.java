package ru.ticket.recognition.style;

/** Визуальный «отпечаток» шрифта — признаки, видимые на скане. */
public record VisualFontProfile(
        double aspectRatio,
        double strokeRatio,
        double xHeightRatio,
        double italicSkew) {
    double estimateSize(int boxHeight) {
        double byXHeight = xHeightRatio * boxHeight / .52;
        double estimate = xHeightRatio > .82 ? boxHeight / .7 : byXHeight;
        return Math.max(8, Math.min(estimate, boxHeight * 1.4));
    }
}
