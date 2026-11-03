package ru.ticket.generation.application.port;

import java.util.List;
import ru.ticket.generation.application.dto.NamedDocument;
import ru.ticket.generation.application.dto.PackResult;

public interface DocumentPackager {
  String packAs();

  PackResult pack(List<NamedDocument> documents);

  default PackResult pack(List<NamedDocument> documents, ru.ticket.export.ExportFormat format) {
    return pack(documents);
  }

  String mimeType();

  String extension();
}
