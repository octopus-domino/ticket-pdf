package ru.ticket.export;

public class UnsupportedFormatException extends RuntimeException {
    public UnsupportedFormatException(String format) {
        super("Unsupported export format: " + format);
    }
}