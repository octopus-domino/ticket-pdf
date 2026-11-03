package ru.ticket.datamodel.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticket.common.ApiException;
import ru.ticket.datamodel.domain.DataModelEntity;
import ru.ticket.datamodel.dto.DataModelDtos.*;
import ru.ticket.datamodel.repository.DataModelRepository;
import ru.ticket.schema.repository.SchemaRepository;

@Service
public class DataModelServiceImpl implements DataModelService {

  private final DataModelRepository models;
  private final SchemaRepository schemas;

  public DataModelServiceImpl(DataModelRepository models, SchemaRepository schemas) {
    this.models = models;
    this.schemas = schemas;
  }

  @Override
  public List<ModelDto> list(String userId, String schemaId) {
    List<DataModelEntity> arr =
        (schemaId == null || schemaId.isBlank())
            ? models.findAllByUserIdOrderByUpdatedAtDesc(userId)
            : models.findAllByUserIdAndSchemaIdOrderByUpdatedAtDesc(userId, schemaId);
    return arr.stream().map(this::toDto).toList();
  }

  @Override
  public ModelDto get(String userId, String modelId) {
    return toDto(find(userId, modelId));
  }

  @Override
  @Transactional
  public ModelDto create(String userId, CreateModelRequest req) {
    assertSchema(userId, req.schemaId());
    return toDto(
        models.save(
            new DataModelEntity(
                userId,
                req.schemaId(),
                req.name().trim(),
                validateDraft(userId, req.schemaId(), req.values()))));
  }

  @Override
  @Transactional
  public ModelDto update(String userId, String modelId, UpdateModelRequest req) {
    DataModelEntity m = find(userId, modelId);
    m.rename(req.name().trim());
    m.replaceValues(validateDraft(userId, m.getSchemaId(), req.values()));
    return toDto(models.save(m));
  }

  @Override
  @Transactional
  public void delete(String userId, String modelId) {
    models.delete(find(userId, modelId));
  }

  @Override
  @Transactional
  public ImportResult importBulk(String userId, ImportRequest req) {
    assertSchema(userId, req.schemaId());
    int imported = 0;
    List<ImportIssue> issues = new ArrayList<>();
    for (int i = 0; i < req.items().size(); i++) {
      ImportItem it = req.items().get(i);
      try {
        if (it.values() == null) {
          issues.add(new ImportIssue(i, "values is null"));
          continue;
        }
        String name =
            (it.name() == null || it.name().isBlank()) ? "model-" + (i + 1) : it.name().trim();
        models.save(
            new DataModelEntity(
                userId, req.schemaId(), name, validateDraft(userId, req.schemaId(), it.values())));
        imported++;
      } catch (Exception e) {
        issues.add(new ImportIssue(i, e.getMessage()));
      }
    }
    return new ImportResult(imported, issues);
  }

  @Override
  public ExportBundle export(String userId, List<String> ids) {
    if (ids == null || ids.isEmpty()) throw ApiException.badRequest("No ids");
    List<DataModelEntity> list = models.findAllByUserIdAndIdIn(userId, ids);
    if (list.isEmpty()) throw ApiException.notFound("DataModel");
    return new ExportBundle(
        "1.0", list.get(0).getSchemaId(), list.stream().map(this::toDto).toList());
  }

  private Map<String, Object> validateDraft(
      String userId, String schemaId, Map<String, Object> values) {
    var fields =
        schemas
            .findByIdAndUserId(schemaId, userId)
            .orElseThrow(() -> ApiException.notFound("Schema"))
            .getFields();
    var optional =
        fields.stream()
            .map(
                f ->
                    new ru.ticket.schema.domain.FieldDef(
                        f.path(), f.type(), false, f.label(), f.defaultValue()))
            .toList();
    return ru.ticket.generation.domain.ModelValues.validate(values, optional);
  }

  private void assertSchema(String userId, String schemaId) {
    if (!schemas.existsByIdAndUserId(schemaId, userId)) throw ApiException.notFound("Schema");
  }

  private DataModelEntity find(String userId, String id) {
    return models
        .findByIdAndUserId(id, userId)
        .orElseThrow(() -> ApiException.notFound("DataModel"));
  }

  private ModelDto toDto(DataModelEntity m) {
    return new ModelDto(
        m.getId(),
        m.getSchemaId(),
        m.getName(),
        java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(m.getValues())),
        m.getCreatedAt(),
        m.getUpdatedAt());
  }
}
