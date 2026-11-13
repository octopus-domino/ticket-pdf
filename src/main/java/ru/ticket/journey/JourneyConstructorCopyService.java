package ru.ticket.journey;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticket.schema.dto.SchemaDtos;
import ru.ticket.schema.service.SchemaService;
import ru.ticket.template.dto.TemplateDtos;
import ru.ticket.template.service.TemplateService;
import ru.ticket.datamodel.dto.DataModelDtos;
import ru.ticket.datamodel.service.DataModelService;

/** A ready publication becomes real constructor resources, atomically and under its owner's account. */
@Service
public class JourneyConstructorCopyService {
  public record Created(String templateId, String schemaId, String modelId, String siteSlug) {}
  private final JourneyService documents;
  private final JourneyDocumentFactory factory;
  private final SchemaService schemas;
  private final TemplateService templates;
  private final DataModelService models;
  public JourneyConstructorCopyService(JourneyService documents, JourneyDocumentFactory factory,
      SchemaService schemas, TemplateService templates, DataModelService models) {
    this.documents = documents; this.factory = factory; this.schemas = schemas; this.templates = templates; this.models = models;
  }
  @Transactional
  public Created copy(String owner, String id, String managementKey) {
    var view = managementKey == null || managementKey.isBlank()
        ? documents.manageOwned(id, owner) : documents.manage(id, managementKey);
    var source = factory.constructorSource(view);
    var fields = source.template().fields().stream().map(field -> new SchemaDtos.FieldDto(
        field.path(), field.type(), field.required(), field.label(), field.defaultValue())).toList();
    var schema = schemas.create(owner, new SchemaDtos.CreateSchemaRequest(view.title(), fields));
    var template = templates.create(owner, new TemplateDtos.SaveTemplateRequest(schema.id(), view.title(),
        source.template().slides(), source.template().bindings(), null));
    var model = models.create(owner, new DataModelDtos.CreateModelRequest(schema.id(), view.title(), source.model()));
    return new Created(template.id(), schema.id(), model.id(), view.data().get("site"));
  }
}
