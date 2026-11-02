package ru.ticket.schema.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class SchemaDtos {
    private SchemaDtos() { }

    public record FieldDto(
            @NotBlank @Size(max = 200) String path,
            @NotBlank @Size(max = 20) String type,
            boolean required,
            @Size(max = 200) String label,
            String defaultValue
    ) { }

    public record SchemaDto(
            String id,
            String name,
            List<FieldDto> fields,
            Instant createdAt,
            Instant updatedAt
    ) { }

    public record CreateSchemaRequest(
            @NotBlank @Size(max = 200) String name,
            @NotNull @Valid List<FieldDto> fields
    ) { }

    public record UpdateSchemaRequest(
            @NotBlank @Size(max = 200) String name,
            @NotNull @Valid List<FieldDto> fields
    ) { }
}