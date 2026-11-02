package ru.ticket.template.service;

import ru.ticket.template.dto.TemplateDtos.SaveTemplateRequest;
import ru.ticket.template.dto.TemplateDtos.TemplateDto;
import ru.ticket.template.dto.TemplateDtos.TemplateSummaryDto;

import java.util.List;

public interface TemplateService {

    List<TemplateSummaryDto> list(String userId, String schemaId);

    TemplateDto get(String userId, String templateId);

    TemplateDto create(String userId, SaveTemplateRequest req);

    TemplateDto update(String userId, String templateId, SaveTemplateRequest req);

    TemplateDto rename(String userId, String templateId, String name);

    void delete(String userId, String templateId);
}