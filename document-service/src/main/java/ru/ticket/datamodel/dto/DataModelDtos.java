package ru.ticket.datamodel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class DataModelDtos {
  private DataModelDtos() {}

  public record ModelDto(
      String id,
      String schemaId,
      String name,
      Map<String, Object> values,
      Instant createdAt,
      Instant updatedAt) {}

  public record CreateModelRequest(
      @NotBlank String schemaId,
      @NotBlank @Size(max = 200) String name,
      @NotNull @Size(max = 500) Map<String, Object> values) {}

  public record UpdateModelRequest(
      @NotBlank @Size(max = 200) String name,
      @NotNull @Size(max = 500) Map<String, Object> values) {}

  public record ImportItem(
      @Size(max = 200) String name, @NotNull @Size(max = 500) Map<String, Object> values,
      @Size(max = 255) String id) {
    public ImportItem(String name, Map<String,Object> values) { this(name, values, null); }
  }

  public record ImportRequest(
      @NotBlank String schemaId,
      @NotNull @Size(min = 1, max = 10000) @jakarta.validation.Valid List<ImportItem> items) {}

  public record ImportIssue(int index, String message) {}

  public record ImportResult(int imported, List<ImportIssue> issues) {}

  public record ExportBundle(String version, String schemaId, List<ModelDto> items) {}
}
