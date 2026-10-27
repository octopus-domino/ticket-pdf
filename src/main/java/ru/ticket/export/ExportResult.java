package ru.ticket.export;

public record ExportResult(
        byte[] content,
        String filename,
        String mimeType
) {}