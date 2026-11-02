package ru.ticket.template.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticket.common.ApiException;
import ru.ticket.schema.repository.SchemaRepository;
import ru.ticket.template.domain.TemplateEntity;
import ru.ticket.template.dto.TemplateDtos.SaveTemplateRequest;
import ru.ticket.template.dto.TemplateDtos.TemplateDto;
import ru.ticket.template.dto.TemplateDtos.TemplateSummaryDto;
import ru.ticket.template.repository.TemplateRepository;

import java.util.List;

@Service
public class TemplateServiceImpl implements TemplateService {

    private final TemplateRepository templates;
    private final SchemaRepository schemas;

    public TemplateServiceImpl(TemplateRepository templates, SchemaRepository schemas) {
        this.templates = templates;
        this.schemas = schemas;
    }

    @Override
    public List<TemplateSummaryDto> list(String userId, String schemaId) {
        List<TemplateEntity> arr = (schemaId == null || schemaId.isBlank())
                ? templates.findAllByUserIdOrderByUpdatedAtDesc(userId)
                : templates.findAllByUserIdAndSchemaIdOrderByUpdatedAtDesc(userId, schemaId);

        return arr.stream()
                .map(t -> new TemplateSummaryDto(
                        t.getId(),
                        t.getSchemaId(),
                        t.getName(),
                        t.getSlides() == null ? 0 : t.getSlides().size(),
                        t.getUpdatedAt()))
                .toList();
    }

    @Override
    public TemplateDto get(String userId, String templateId) {
        return toDto(find(userId, templateId));
    }

    @Override
    @Transactional
    public TemplateDto create(String userId, SaveTemplateRequest req) {
        assertSchema(userId, req.schemaId());
        TemplateEntity t = new TemplateEntity(
                userId,
                blankToNull(req.schemaId()),
                req.name().trim(),
                req.slides(),
                req.bindings()
        );
        return toDto(templates.save(t));
    }

    @Override
    @Transactional
    public TemplateDto update(String userId, String templateId, SaveTemplateRequest req) {
        TemplateEntity t = find(userId, templateId);
        assertSchema(userId, req.schemaId());
        t.replace(
                blankToNull(req.schemaId()),
                req.name().trim(),
                req.slides(),
                req.bindings()
        );
        return toDto(templates.save(t));
    }

    @Override
    @Transactional
    public TemplateDto rename(String userId, String templateId, String name) {
        TemplateEntity t = find(userId, templateId);
        t.rename(name.trim());
        return toDto(templates.save(t));
    }

    @Override
    @Transactional
    public void delete(String userId, String templateId) {
        TemplateEntity t = find(userId, templateId);
        templates.delete(t);
    }

    /* ============================================================ */

    private TemplateEntity find(String userId, String id) {
        return templates.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Template"));
    }

    private void assertSchema(String userId, String schemaId) {
        if (schemaId == null || schemaId.isBlank()) return;
        if (!schemas.existsByIdAndUserId(schemaId, userId)) {
            throw ApiException.notFound("Schema");
        }
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private TemplateDto toDto(TemplateEntity t) {
        return new TemplateDto(
                t.getId(),
                t.getSchemaId(),
                t.getName(),
                t.getSlides(),
                t.getBindings(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}