package ru.ticket.schema.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class SchemaDtos {
  private SchemaDtos() {}

  public record FieldDto(
      @NotBlank
          @Size(max = 200)
          @jakarta.validation.constraints.Pattern(
              regexp = "(?!.*(?:__proto__|prototype|constructor))[a-zA-Z_][a-zA-Z0-9_.]*")
          String path,
      @NotBlank
          @jakarta.validation.constraints.Pattern(
              regexp = "string|number|date|boolean|image|object|array")
          String type,
      boolean required,
      @Size(max = 200) String label,
      String defaultValue) {}

  public record SchemaDto(
      String id, String name, List<FieldDto> fields, Instant createdAt, Instant updatedAt) {}

  public record CreateSchemaRequest(
      @NotBlank @Size(max = 200) String name,
      @NotNull @Size(max = 500) @Valid List<FieldDto> fields) {}

  public record UpdateSchemaRequest(
      @NotBlank @Size(max = 200) String name,
      @NotNull @Size(max = 500) @Valid List<FieldDto> fields) {}
}
