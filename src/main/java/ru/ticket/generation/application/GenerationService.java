package ru.ticket.generation.application;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import ru.ticket.common.ApiException;
import ru.ticket.dto.Slide;
import ru.ticket.export.ExportFormat;
import ru.ticket.generation.application.dto.*;
import ru.ticket.generation.application.port.*;
import ru.ticket.generation.domain.*;

/** Application use case; storage and rendering are replaceable ports. */
@Service
public class GenerationService {
  private static final long MAX_RESULT_BYTES = 32L * 1024 * 1024;
  private final ru.ticket.common.HeavyOperationCapacity capacity;
  private final Map<ExportFormat, DocumentRenderer> renderers;
  private final Map<String, DocumentPackager> packagers;
  private final GenerationCatalog catalog;

  public GenerationService(
      List<DocumentRenderer> renderers,
      List<DocumentPackager> packagers,
      GenerationCatalog catalog,
      ru.ticket.common.HeavyOperationCapacity capacity) {
    this.renderers = renderers.stream().collect(Collectors.toMap(DocumentRenderer::format, r -> r));
    this.packagers = packagers.stream().collect(Collectors.toMap(DocumentPackager::packAs, p -> p));
    this.catalog = catalog;
    this.capacity = capacity;
  }

  public record SyncResult(PackResult packed, int count, List<GenerationJob.ItemError> issues) {}

  public record DocumentsResult(
      List<NamedDocument> documents, List<GenerationJob.ItemError> issues) {}

  @FunctionalInterface
  public interface ItemCallback {
    void onItem(int index, int completed, int failed);
  }

  public SyncResult generateSync(String userId, GenerationRequest req) {
    var result = generateDocuments(userId, req, (i, c, f) -> {});
    if (result.documents().isEmpty())
      throw ApiException.badRequest("All items failed: " + result.issues());
    return new SyncResult(
        pack(req.packAs, req.format, result.documents()),
        req.items.size() - result.issues().size(),
        result.issues());
  }

  public DocumentsResult generateDocuments(
      String userId, GenerationRequest req, ItemCallback progress) {
    return generateDocuments(userId, req, progress, null);
  }

  public GenerationSource freezeSource(String userId, GenerationRequest request) {
    var ids =
        request.items.stream()
            .map(item -> item.dataModelId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    var source =
        new GenerationSource(catalog.template(userId, request), catalog.models(userId, ids));
    try {
      var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
      byte[] bytes = mapper.writeValueAsBytes(source);
      if (bytes.length > 20 * 1024 * 1024)
        throw ApiException.badRequest("Generation snapshot exceeds 20 MB; split the batch");
      return mapper.readValue(bytes, GenerationSource.class);
    } catch (java.io.IOException error) {
      throw new IllegalArgumentException("Cannot snapshot generation source", error);
    }
  }

  public DocumentsResult generateDocuments(
      String userId, GenerationRequest req, ItemCallback progress, GenerationSource source) {
    if (!capacity.tryAcquire())
      throw new ApiException(
          org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
          "Generation is busy. Retry shortly.");
    try {
      return renderDocuments(userId, req, progress, source);
    } finally {
      capacity.release();
    }
  }

  private DocumentsResult renderDocuments(
      String userId, GenerationRequest req, ItemCallback progress, GenerationSource source) {
    if (req.items == null || req.items.isEmpty() || req.items.size() > 10000)
      throw ApiException.badRequest("Expected 1..10000 items");
    var template = source == null ? catalog.template(userId, req) : source.template();
    var renderer = rendererFor(ExportFormat.fromString(req.format));
    if (req.options != null && !"pdf/a-2b".equals(req.options.pdfVersion))
      throw ApiException.badRequest("Unsupported PDF version");
    if (req.options != null && !req.options.embedImages && !"svg".equals(req.format))
      throw ApiException.badRequest("PDF requires embedImages=true");
    var ids =
        req.items.stream()
            .map(i -> i.dataModelId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    var models = source == null ? catalog.models(userId, ids) : source.models();
    Set<String> known = template.fields().stream().map(f -> f.path()).collect(Collectors.toSet());
    for (var binding : template.bindings()) {
      if ("ignored".equals(binding.status)) continue;
      String path =
          binding.schemaPath == null ? "" : binding.schemaPath.replaceAll("\\[\\d+\\]", "");
      var field =
          template.fields().stream().filter(f -> f.path().equals(path)).findFirst().orElse(null);
      boolean invalidType =
          field != null
              && binding.expectedType != null
              && (Set.of("image", "array").contains(binding.expectedType)
                  ? !binding.expectedType.equals(field.type())
                  : Set.of("image", "array", "object").contains(field.type()));
      if (!known.isEmpty() && !known.contains(path)
          || "invalid-type".equals(binding.status)
          || invalidType) throw ApiException.badRequest("Invalid binding: " + binding.schemaPath);
    }
    List<NamedDocument> docs = new ArrayList<>();
    List<GenerationJob.ItemError> errors = new ArrayList<>();
    int completed = 0, failed = 0;
    long bytes = 0;
    for (int i = 0; i < req.items.size(); i++) {
      progress.onItem(i, completed, failed); // cancellation before expensive work
      var item = req.items.get(i);
      try {
        var model = item.dataModelId == null ? null : models.get(item.dataModelId);
        if (item.dataModelId != null && model == null)
          throw new IllegalArgumentException("DataModel not found");
        if (model != null
            && template.schemaId() != null
            && !template.schemaId().equals(model.schemaId()))
          throw new IllegalArgumentException("Model schema mismatch");
        Map<String, Object> values =
            item.inlineValues != null ? item.inlineValues : model == null ? null : model.values();
        values = ModelValues.validate(values, template.fields());
        List<Slide> filled = new ArrayList<>();
        for (Slide slide : template.slides())
          filled.add(DataFiller.fill(slide, values, template.bindings()));
        Map<String, Object> names = new LinkedHashMap<>(values);
        names.putIfAbsent("name", model == null ? "document-" + (i + 1) : model.name());
        String pattern =
            item.filenameOverride == null ? req.filenamePattern : item.filenameOverride;
        String name =
            FilenameTemplate.resolve(pattern, names, "document-" + (i + 1))
                .replaceAll("(?i)\\.(svg|pdf|pdfa\\.pdf)$", "");
        var rendered = renderer.renderMulti(filled, name, req.options);
        long next = rendered.stream().mapToLong(d -> d.content().length).sum();
        if (bytes + next > MAX_RESULT_BYTES) throw new ResultLimitExceeded();
        docs.addAll(rendered);
        bytes += next;
        completed++;
      } catch (CancellationException cancelled) {
        throw cancelled;
      } catch (ResultLimitExceeded oversized) {
        throw ApiException.badRequest("Batch exceeds 32 MB; split the batch");
      } catch (Exception error) {
        failed++;
        errors.add(
            new GenerationJob.ItemError(
                i, error.getMessage() == null ? "Rendering failed" : error.getMessage()));
      }
      progress.onItem(i, completed, failed);
    }
    return new DocumentsResult(docs, errors);
  }

  public PackResult pack(String packAs, String format, List<NamedDocument> docs) {
    if (!capacity.tryAcquire())
      throw new ApiException(
          org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
          "Generation is busy. Retry shortly.");
    try {
      return packDocuments(packAs, format, docs);
    } finally {
      capacity.release();
    }
  }

  private PackResult packDocuments(String packAs, String format, List<NamedDocument> docs) {
    if (docs == null || docs.isEmpty()) throw ApiException.badRequest("No documents to pack");
    String key = packAs == null ? "zip" : packAs;
    if ("single".equals(key)) {
      if (docs.size() != 1) throw ApiException.badRequest("single requires exactly one document");
      var document = docs.get(0);
      return new PackResult(
          document.content(),
          document.filename(),
          rendererFor(ExportFormat.fromString(format)).mimeType());
    }
    var packager = packagers.get(key);
    if (packager == null) throw ApiException.badRequest("Unsupported packAs: " + key);
    if ("multipage-pdf".equals(key) && "svg".equals(format))
      throw ApiException.badRequest("SVG cannot be packed as PDF");
    return packager.pack(docs, ExportFormat.fromString(format));
  }

  public DocumentRenderer rendererFor(ExportFormat format) {
    var renderer = renderers.get(format);
    if (renderer == null) throw ApiException.badRequest("Unsupported format");
    return renderer;
  }

  private static final class ResultLimitExceeded extends RuntimeException {}
}
