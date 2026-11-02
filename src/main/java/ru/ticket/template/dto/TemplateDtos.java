package ru.ticket.template.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.ticket.dto.Slide;
import ru.ticket.template.domain.BindingDto;

import java.time.Instant;
import java.util.List;

public final class TemplateDtos {

    private TemplateDtos() { }

    public record TemplateDto(
            String id,
            String schemaId,
            String name,
            List<Slide> slides,
            List<BindingDto> bindings,
            Instant createdAt,
            Instant updatedAt
    ) { }

    public record TemplateSummaryDto(
            String id,
            String schemaId,
            String name,
            int slideCount,
            Instant updatedAt
    ) { }

    public record SaveTemplateRequest(
            @Size(max = 36) String schemaId,
            @NotBlank @Size(max = 200) String name,
            @NotNull @Valid List<Slide> slides,
            @Valid List<BindingDto> bindings
    ) { }

    public record RenameTemplateRequest(
            @NotBlank @Size(max = 200) String name
    ) { }
}