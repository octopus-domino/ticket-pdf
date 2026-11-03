package ru.ticket.generation.infrastructure;

import java.util.*;
import org.springframework.stereotype.Component;
import ru.ticket.common.ApiException;
import ru.ticket.datamodel.repository.DataModelRepository;
import ru.ticket.generation.application.dto.GenerationRequest;
import ru.ticket.generation.application.port.GenerationCatalog;
import ru.ticket.schema.repository.SchemaRepository;
import ru.ticket.template.repository.TemplateRepository;

@Component
public class JpaGenerationCatalog implements GenerationCatalog {
  private final TemplateRepository templates;
  private final SchemaRepository schemas;
  private final DataModelRepository models;

  public JpaGenerationCatalog(
      TemplateRepository templates, SchemaRepository schemas, DataModelRepository models) {
    this.templates = templates;
    this.schemas = schemas;
    this.models = models;
  }

  public Template template(String userId, GenerationRequest req) {
    var slides = req.inlineSlides;
    var schemaId = req.schemaId;
    var bindings = req.bindings;
    if (req.templateId != null && !req.templateId.isBlank()) {
      var template =
          templates
              .findByIdAndUserId(req.templateId, userId)
              .orElseThrow(() -> ApiException.notFound("Template"));
      slides = template.getSlides();
      schemaId = template.getSchemaId();
      bindings = template.getBindings();
    }
    if (slides == null || slides.isEmpty()) throw ApiException.badRequest("Template has no slides");
    var fields =
        schemaId == null || schemaId.isBlank()
            ? List.<ru.ticket.schema.domain.FieldDef>of()
            : schemas
                .findByIdAndUserId(schemaId, userId)
                .orElseThrow(() -> ApiException.notFound("Schema"))
                .getFields();
    return new Template(slides, schemaId, fields, bindings == null ? List.of() : bindings);
  }

  public Map<String, Model> models(String userId, Collection<String> ids) {
    Map<String, Model> result = new HashMap<>();
    if (!ids.isEmpty())
      for (var model : models.findAllByUserIdAndIdIn(userId, new ArrayList<>(ids))) {
        result.put(
            model.getId(), new Model(model.getSchemaId(), model.getName(), model.getValues()));
      }
    return result;
  }
}
