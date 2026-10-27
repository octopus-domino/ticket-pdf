package ru.ticket.export;

public enum ExportFormat {
    SVG,
    PDF,
    PDFA;

    public static ExportFormat fromString(String s) {
        if (s == null) return PDFA;
        return switch (s.toLowerCase()) {
            case "svg" -> SVG;
            case "pdf" -> PDF;
            case "pdfa" -> PDFA;
            default -> throw new UnsupportedFormatException(s);
        };
    }
}