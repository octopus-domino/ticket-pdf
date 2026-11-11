package ru.ticket.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticket.common.ApiException;
import ru.ticket.generation.application.GenerationService;
import ru.ticket.generation.application.dto.*;

@Service
public class WorkflowService {
  public record Managed(
      WorkflowDefinition definition,
      long revision,
      boolean published,
      boolean shared,
      boolean administrator) {}

  public record PublicView(WorkflowDefinition definition, long revision) {}

  private final WorkflowRepository repository;
  private final ObjectMapper mapper;
  private final GenerationService generation;
  private final Set<String> administrators;
  private final SiteAddressService addresses;

  public WorkflowService(
      WorkflowRepository repository,
      ObjectMapper mapper,
      GenerationService generation,
      SiteAddressService addresses,
      @Value("${app.workflow.admin-user-ids:}") String administrators) {
    this.repository = repository;
    this.mapper = mapper;
    this.generation = generation;
    this.addresses = addresses;
    this.administrators =
        new HashSet<>(
            Arrays.stream(administrators.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList());
  }

  @Transactional(readOnly = true)
  public String availability(String slug) {
    return addresses.availability(slug);
  }

  @Transactional(readOnly = true)
  public List<Managed> list(String owner) {
    var records = new LinkedHashMap<String, WorkflowRepository.Summary>();
    repository
        .findTop100ByOwnerIdOrderByUpdatedAtDesc(owner)
        .forEach(e -> records.put(e.getSlug(), e));
    if (administrators.contains(owner))
      repository
          .findTop100BySharedTrueOrderByUpdatedAtDesc()
          .forEach(e -> records.put(e.getSlug(), e));
    return records.values().stream().map(e -> managed(e, owner)).toList();
  }

  public List<WorkflowDefinition.Field> templateFields(String owner, String template) {
    return generation.freezeSource(owner, request(template, Map.of())).template().fields().stream()
        .filter(f -> !Set.of("array", "object", "image").contains(f.type()))
        .map(
            f ->
                new WorkflowDefinition.Field(
                    f.path(),
                    f.label(),
                    switch (f.type()) {
                      case "number", "date" -> f.type();
                      case "boolean" -> "boolean";
                      default -> "text";
                    },
                    f.required(),
                    List.of()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<PublicView> catalog() {
    return repository.findTop100BySharedTrueAndPublishedIsNotNullOrderByUpdatedAtDesc().stream()
        .map(
            e ->
                new PublicView(
                    read(e.getPublished(), WorkflowDefinition.class), e.getPublishedRevision()))
        .toList();
  }

  @Transactional
  public Managed save(String owner, WorkflowDefinition definition, Long expectedVersion) {
    definition.validate();
    var existing = repository.findById(definition.slug());
    WorkflowEntity entity;
    if (existing.isPresent()) {
      entity = existing.get();
      authorize(entity, owner);
      if (expectedVersion == null || entity.revision != expectedVersion)
        throw ApiException.conflict("Сценарий уже изменён. Обновите страницу перед сохранением");
    } else {
      addresses.reserve(definition.slug(), "workflow");
      if (repository.countByOwnerId(owner) >= 100)
        throw ApiException.badRequest("Достигнут лимит: 100 сценариев на пользователя");
      if (expectedVersion != null) throw ApiException.notFound("Workflow");
      entity = new WorkflowEntity();
      entity.slug = definition.slug();
      entity.ownerId = owner;
    }
    // Ownership is checked even before publication; an arbitrary foreign template cannot be saved.
    generation.freezeSource(entity.ownerId, request(definition.templateId(), Map.of()));
    entity.draft = json(definition);
    entity.revision++;
    entity.updatedAt = Instant.now();
    repository.saveAndFlush(entity);
    return managed(entity, owner);
  }

  @Transactional
  public Managed publish(String owner, String slug, long revision, boolean shared) {
    var entity = owned(owner, slug);
    if (entity.revision != revision)
      throw ApiException.conflict("Сохраните актуальную версию перед публикацией");
    if (shared && !administrators.contains(owner))
      throw ApiException.forbidden("Общими сценариями управляет администратор");
    var definition = read(entity.draft, WorkflowDefinition.class);
    definition.validate();
    var source =
        generation.freezeSource(entity.ownerId, request(definition.templateId(), Map.of()));
    var paths =
        definition.steps().stream()
            .flatMap(s -> s.fields().stream())
            .map(WorkflowDefinition.Field::path)
            .collect(java.util.stream.Collectors.toSet());
    for (var field : source.template().fields())
      if (field.required() && field.defaultValue() == null && !paths.contains(field.path()))
        throw ApiException.badRequest("Добавьте обязательное поле шаблона: " + field.label());
    String snapshot = json(source);
    if (snapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 2 * 1024 * 1024)
      throw ApiException.badRequest("Шаблон сценария превышает 2 МБ. Уменьшите изображения");
    entity.published = entity.draft;
    entity.source = snapshot;
    entity.shared = shared;
    entity.revision++;
    entity.publishedRevision = entity.revision;
    entity.updatedAt = Instant.now();
    repository.save(entity);
    return managed(entity, owner);
  }

  @Transactional
  public void unpublish(String owner, String slug) {
    var entity = owned(owner, slug);
    entity.published = null;
    entity.source = null;
    entity.revision++;
    entity.updatedAt = Instant.now();
  }

  @Transactional(readOnly = true)
  public PublicView publicView(String slug) {
    var entity = published(slug);
    return new PublicView(
        read(entity.published, WorkflowDefinition.class), entity.publishedRevision);
  }

  public PackResult generate(String slug, long revision, Map<String, Object> input) {
    var entity = published(slug);
    if (revision != entity.publishedRevision)
      throw ApiException.conflict("Форма обновилась. Откройте сценарий заново");
    var definition = read(entity.published, WorkflowDefinition.class);
    var request = request(definition.templateId(), definition.values(input));
    var result =
        generation.generateDocuments(
            entity.ownerId, request, (i, c, f) -> {}, read(entity.source, GenerationSource.class));
    if (result.documents().isEmpty())
      throw ApiException.badRequest("Не удалось заполнить шаблон. Проверьте данные формы");
    return generation.pack("multipage-pdf", "pdf", result.documents());
  }

  private WorkflowEntity published(String slug) {
    var entity = repository.findById(slug).orElseThrow(() -> ApiException.notFound("Workflow"));
    if (entity.published == null) throw ApiException.notFound("Workflow");
    return entity;
  }

  private WorkflowEntity owned(String owner, String slug) {
    var e = repository.findById(slug).orElseThrow(() -> ApiException.notFound("Workflow"));
    authorize(e, owner);
    return e;
  }

  private void authorize(WorkflowEntity entity, String owner) {
    if (!entity.ownerId.equals(owner) && !(entity.shared && administrators.contains(owner)))
      throw ApiException.forbidden("Этот сценарий принадлежит другому пользователю");
  }

  private Managed managed(WorkflowEntity e, String owner) {
    return new Managed(
        read(e.draft, WorkflowDefinition.class),
        e.revision,
        e.published != null,
        e.shared,
        administrators.contains(owner));
  }

  private Managed managed(WorkflowRepository.Summary e, String owner) {
    return new Managed(
        read(e.getDraft(), WorkflowDefinition.class),
        e.getRevision(),
        e.getPublished() != null,
        e.getShared(),
        administrators.contains(owner));
  }

  private GenerationRequest request(String template, Map<String, Object> values) {
    var r = new GenerationRequest();
    r.templateId = template;
    r.format = "pdf";
    r.packAs = "multipage-pdf";
    var item = new GenerationRequest.Item();
    item.inlineValues = values;
    r.items = List.of(item);
    return r;
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (java.io.IOException error) {
      throw new IllegalStateException(error);
    }
  }

  private <T> T read(String value, Class<T> type) {
    try {
      return mapper.readValue(value, type);
    } catch (java.io.IOException error) {
      throw new IllegalStateException(error);
    }
  }
}
