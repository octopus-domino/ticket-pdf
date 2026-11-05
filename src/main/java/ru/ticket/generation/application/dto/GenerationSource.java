package ru.ticket.generation.application.dto;

import java.util.Map;
import ru.ticket.generation.application.port.GenerationCatalog;

/** Immutable serialized source used by queued jobs and retries, independent of later edits. */
public record GenerationSource(GenerationCatalog.Template template, Map<String, GenerationCatalog.Model> models) {}
