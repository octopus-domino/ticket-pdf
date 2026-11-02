package ru.ticket.generation.application.port;

import ru.ticket.generation.application.dto.NamedDocument;
import ru.ticket.generation.application.dto.PackResult;

import java.util.List;

public interface DocumentPackager {
    String packAs();
    PackResult pack(List<NamedDocument> documents);
    String mimeType();
    String extension();
}