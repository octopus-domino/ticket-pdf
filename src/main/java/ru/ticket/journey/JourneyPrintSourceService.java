package ru.ticket.journey;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticket.common.ApiException;
import ru.ticket.datamodel.service.DataModelService;
import ru.ticket.generation.application.port.GenerationCatalog;
import ru.ticket.generation.domain.TemplateMaterializer;
import ru.ticket.schema.domain.FieldDef;
import ru.ticket.schema.service.SchemaService;
import ru.ticket.template.service.TemplateService;

/** Publication commands only: account ownership is checked before freezing constructor resources. */
@Service
public class JourneyPrintSourceService {
  public record Input(String templateId, String modelId) {}
  private final JourneyPrintSourceRepository repository;
  private final JourneyService documents;
  private final PublicationAccess access;
  private final TemplateService templates;
  private final DataModelService models;
  private final SchemaService schemas;
  private final ObjectMapper mapper;
  private final JourneyDocumentFactory factory;
  private final JourneyAlbumService albums;
  public JourneyPrintSourceService(JourneyPrintSourceRepository repository, JourneyService documents,
      PublicationAccess access, TemplateService templates, DataModelService models, SchemaService schemas, ObjectMapper mapper, JourneyDocumentFactory factory, JourneyAlbumService albums) {
    this.repository = repository; this.documents = documents; this.access = access; this.templates = templates;
    this.models = models; this.schemas = schemas; this.mapper = mapper; this.factory = factory; this.albums = albums;
  }
  @Transactional
  public void publication(String owner, String id, String key, Input input) {
    if (key == null || key.isBlank()) documents.manageOwned(id, owner); else documents.manage(id, key);
    save("document:" + id, source(owner, input));
  }
  @Transactional
  public void site(String owner, String slug, Input input) {
    access.site(slug, owner);
    save("site:" + slug, source(owner, input));
  }
  @Transactional
  public void batch(String owner, String id, String key, Input input) {
    var batch = key == null || key.isBlank() ? documents.batchOwned(id, owner) : documents.batch(id, key);
    var design = source(owner, input);
    int totalBytes = 0;
    for (var issued : batch.documents()) {
      var current = factory.constructorSource(issued.document());
      if (current.template().slides().size() != design.template().slides().size()
          || !contentShape(current.model()).equals(contentShape(design.model())))
        throw ApiException.conflict("Release documents use different page structures; apply the template separately");
      // Design is shared; each recipient, QR, map and model value belongs to its original document.
      var model = new LinkedHashMap<String, Object>(design.model()); model.putAll(current.model());
      var packet = new JourneyConstructorSource.Packet(design.template(), model, design.options());
      TemplateMaterializer.fill(packet.template(), packet.model());
      try { totalBytes += mapper.writeValueAsBytes(packet).length; }
      catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
      if (totalBytes > 32 * 1024 * 1024) throw ApiException.badRequest("Release sources exceed 32 MiB; split the release");
      save("document:" + issued.document().id(), packet);
    }
  }
  private static List<String> contentShape(Map<String, Object> model) {
    if (!(model.get("content") instanceof List<?> content)) return List.of();
    return content.stream().map(row -> row instanceof Map<?, ?> fields ? new TreeSet<>(fields.keySet().stream().map(String::valueOf).toList()).toString() : "invalid").toList();
  }
  @Transactional
  public JourneyService.Created createPublication(String owner, String id, String key, Input input) {
    var original = key == null || key.isBlank() ? documents.manageOwned(id, owner) : documents.manage(id, key);
    var packet = source(owner, input);
    var data = new LinkedHashMap<String, String>(original.data());
    String title = original.title();
    if (packet.model().get("fields") instanceof Map<?, ?> fields) {
      for (var entry : fields.entrySet()) if (entry.getKey() instanceof String name && entry.getValue() instanceof String value) {
        if (name.equals("title")) title = value;
        else if (!Set.of("publication_url", "publication_id", "site").contains(name)) data.put(name, value);
      }
    }
    var created = documents.create(new JourneyService.Input(original.kind(), title, data, List.of()), owner).get(0);
    if (original.kind() == JourneyService.Kind.ALBUM) albums.copyModel(created.document().id(), packet.model().get("photos"));
    save("document:" + created.document().id(), packet);
    return created;
  }
  private JourneyConstructorSource.Packet source(String owner, Input input) {
    if (input == null || input.templateId() == null || input.modelId() == null)
      throw ApiException.badRequest("Choose a template and data model");
    var template = templates.get(owner, input.templateId());
    var model = models.get(owner, input.modelId());
    if (template.schemaId() == null || !template.schemaId().equals(model.schemaId()))
      throw ApiException.badRequest("Template and data model must share a schema");
    var schema = schemas.get(owner, template.schemaId());
    var fields = schema.fields().stream().map(field -> new FieldDef(field.path(), field.type(), field.required(), field.label(), field.defaultValue())).toList();
    var nativeTemplate = new GenerationCatalog.Template(template.slides(), schema.id(), fields, template.bindings());
    TemplateMaterializer.fill(nativeTemplate, model.values());
    return new JourneyConstructorSource.Packet(nativeTemplate, model.values(), new ru.ticket.dto.ExportOptions());
  }
  private void save(String id, JourneyConstructorSource.Packet packet) {
    try {
      String json = mapper.writeValueAsString(packet);
      if (json.length() > 20 * 1024 * 1024) throw ApiException.badRequest("Constructor source exceeds 20 MiB");
      var row = repository.findById(id).orElseGet(JourneyPrintSource::new);
      row.id = id; row.sourceJson = json; row.updatedAt = Instant.now(); repository.save(row);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException(ex); }
  }
}
