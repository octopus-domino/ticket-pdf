package ru.ticket.recognition.style;

/** A visual font estimate in image pixels, independent of OCR's point-size hints. */
public record FontMatch(String family, double size, boolean bold, boolean italic, boolean reliable) {
    public FontMatch(String family, double size, boolean bold, boolean italic) {
        this(family, size, bold, italic, false);
    }
}
