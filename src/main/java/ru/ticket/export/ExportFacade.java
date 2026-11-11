package ru.ticket.export;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import ru.ticket.dto.ExportRequest;

/**
 * Facade скрывает от контроллера детали выбора экспортёра. Паттерн Registry: все DocumentExporter'ы
 * автоматически собираются Spring-ом и раскладываются по формату.
 */
@Service
public class ExportFacade {

  // Shared by HTTP exports, journeys and background callers. Do not queue large requests.
  private final ru.ticket.common.HeavyOperationCapacity capacity;

  private final Map<ExportFormat, DocumentExporter> exporters = new EnumMap<>(ExportFormat.class);

  public ExportFacade(
      List<DocumentExporter> allExporters, ru.ticket.common.HeavyOperationCapacity capacity) {
    this.capacity = capacity;
    for (DocumentExporter exp : allExporters) {
      exporters.put(exp.format(), exp);
    }
  }

  public ExportResult export(ExportFormat format, ExportRequest request) {
    DocumentExporter exporter = exporters.get(format);
    if (exporter == null) {
      throw new UnsupportedFormatException(format.name());
    }
    if (!capacity.tryAcquire()) {
      throw new ru.ticket.common.ApiException(
          org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
          "Export is busy. Please retry shortly.");
    }
    try {
      return exporter.export(request);
    } finally {
      capacity.release();
    }
  }
}
