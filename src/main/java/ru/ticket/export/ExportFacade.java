package ru.ticket.export;

import ru.ticket.dto.ExportRequest;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Facade скрывает от контроллера детали выбора экспортёра.
 * Паттерн Registry: все DocumentExporter'ы автоматически
 * собираются Spring-ом и раскладываются по формату.
 */
@Service
public class ExportFacade {

    private final Map<ExportFormat, DocumentExporter> exporters =
            new EnumMap<>(ExportFormat.class);

    public ExportFacade(List<DocumentExporter> allExporters) {
        for (DocumentExporter exp : allExporters) {
            exporters.put(exp.format(), exp);
        }
    }

    public ExportResult export(ExportFormat format, ExportRequest request) {
        DocumentExporter exporter = exporters.get(format);
        if (exporter == null) {
            throw new UnsupportedFormatException(format.name());
        }
        return exporter.export(request);
    }
}