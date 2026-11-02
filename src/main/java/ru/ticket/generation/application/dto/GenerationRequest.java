package ru.ticket.generation.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.ticket.dto.ExportOptions;
import ru.ticket.dto.Slide;

import java.util.List;
import java.util.Map;

public class GenerationRequest {
    public String templateId;
    @Valid @Size(max = 50) public List<Slide> inlineSlides;
    @NotNull @Size(min = 1, max = 10_000) @Valid public List<Item> items;
    public String format = "pdfa";
    public String packAs = "zip";
    public String filenamePattern = "document-{{name}}";
    @Valid public ExportOptions options = new ExportOptions();

    public static class Item {
        public String dataModelId;
        @Valid public Map<String, Object> inlineValues;
        public String filenameOverride;
    }
}