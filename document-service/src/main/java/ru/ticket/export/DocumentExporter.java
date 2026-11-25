package ru.ticket.export;

import ru.ticket.dto.ExportRequest;

/**
 * Единый контракт экспортёра. Реализации:
 *  - SVG-экспорт
 *  - PDF-экспорт (быстрый, Batik)
 *  - PDF/A-экспорт (типографский, PDFBox)
 *
 * Controller и Facade знают только об этом интерфейсе.
 */
public interface DocumentExporter {
    ExportFormat format();
    ExportResult export(ExportRequest request);
}