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
    private final ru.ticket.workspace.repository.FolderRepository folders;
    private final ru.ticket.history.ResourceHistory history;

    public TemplateServiceImpl(TemplateRepository templates, SchemaRepository schemas,
            ru.ticket.workspace.repository.FolderRepository folders,ru.ticket.history.ResourceHistory history) {
        this.templates = templates;
        this.schemas = schemas;
        this.folders = folders;
        this.history = history;
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
                        t.getUpdatedAt(), t.getFolderId()))
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
        String folderId = assertFolder(userId, req.folderId());
        TemplateEntity t = new TemplateEntity(
                userId,
                blankToNull(req.schemaId()),
                req.name().trim(),
                req.slides(),
                req.bindings()
        );
        t.moveTo(folderId);
        var dto=toDto(templates.save(t));history.capture(userId,"template",t.getId(),dto);return dto;
    }

    @Override
    @Transactional
    public TemplateDto update(String userId, String templateId, SaveTemplateRequest req) {
        TemplateEntity t = find(userId, templateId);
        history.capture(userId,"template",t.getId(),toDto(t));
        assertSchema(userId, req.schemaId());
        t.replace(
                blankToNull(req.schemaId()),
                req.name().trim(),
                req.slides(),
                req.bindings()
        );
        var dto=toDto(templates.save(t));history.capture(userId,"template",t.getId(),dto);return dto;
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

    @Override
    @Transactional
    public TemplateDto move(String userId, String templateId, String folderId) {
        TemplateEntity template = find(userId, templateId);
        template.moveTo(assertFolder(userId, folderId));
        return toDto(templates.save(template));
    }

    private String assertFolder(String userId, String folderId) {
        String normalized = blankToNull(folderId);
        if (normalized != null && folders.findByIdAndUserId(normalized, userId).isEmpty())
            throw ApiException.notFound("Folder");
        return normalized;
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
                t.getUpdatedAt(), t.getFolderId()
        );
    }
}
