package ru.ticket.generation.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import ru.ticket.dto.ExportOptions;
import ru.ticket.dto.Slide;

public class GenerationRequest {
  public String templateId;
  public String schemaId;
  @Valid public List<ru.ticket.template.domain.BindingDto> bindings = List.of();

  @Valid
  @Size(max = 50)
  public List<Slide> inlineSlides;

  @NotNull
  @Size(min = 1, max = 10_000)
  @Valid
  public List<Item> items;

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
