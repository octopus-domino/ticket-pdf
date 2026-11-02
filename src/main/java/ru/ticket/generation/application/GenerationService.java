package ru.ticket.generation.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.ticket.common.ApiException;
import ru.ticket.datamodel.domain.DataModelEntity;
import ru.ticket.datamodel.repository.DataModelRepository;
import ru.ticket.dto.Slide;
import ru.ticket.export.ExportFormat;
import ru.ticket.generation.application.dto.*;
import ru.ticket.generation.application.port.DocumentPackager;
import ru.ticket.generation.application.port.DocumentRenderer;
import ru.ticket.generation.domain.DataFiller;
import ru.ticket.generation.domain.FilenameTemplate;
import ru.ticket.template.domain.TemplateEntity;
import ru.ticket.template.repository.TemplateRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class GenerationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationService.class);

    private final Map<ExportFormat, DocumentRenderer> renderers;
    private final Map<String, DocumentPackager> packagers;
    private final DataModelRepository models;
    private final TemplateRepository templates;

    public GenerationService(List<DocumentRenderer> renderers,
                             List<DocumentPackager> packagers,
                             DataModelRepository models,
                             TemplateRepository templates) {
        this.renderers = renderers.stream()
                .collect(Collectors.toMap(DocumentRenderer::format, r -> r));
        this.packagers = packagers.stream()
                .collect(Collectors.toMap(DocumentPackager::packAs, p -> p));
        this.models = models;
        this.templates = templates;
    }

    /* ============================================================
       Sync use case
       ============================================================ */

    public SyncResult generateSync(String userId, GenerationRequest req) {
        DocumentsResult r = generateDocuments(userId, req, (idx, ok, fail) -> { });
        if (r.documents().isEmpty()) {
            throw ApiException.badRequest("All items failed: " +
                    r.issues().stream().map(GenerationJob.ItemError::message).toList());
        }
        PackResult packed = pack(req.packAs, req.format, r.documents());
        log.info("generation.sync_done user={} count={} failed={} format={} packAs={}",
                userId, r.documents().size(), r.issues().size(), req.format, req.packAs);
        return new SyncResult(packed, r.documents().size(), r.issues());
    }

    public record SyncResult(
            PackResult packed,
            int count,
            List<GenerationJob.ItemError> issues
    ) { }

    /* ============================================================
       Общий пайплайн
       ============================================================ */

    public DocumentsResult generateDocuments(String userId,
                                             GenerationRequest req,
                                             ItemCallback onItem) {
        List<Slide> template = requireTemplate(req, userId);
        DocumentRenderer renderer = rendererFor(ExportFormat.fromString(req.format));
        Map<String, DataModelEntity> byId = loadModels(userId, req.items);

        List<NamedDocument> docs = new ArrayList<>();
        List<GenerationJob.ItemError> issues = new ArrayList<>();
        int completed = 0;
        int failed = 0;

        for (int idx = 0; idx < req.items.size(); idx++) {
            GenerationRequest.Item item = req.items.get(idx);
            try {
                Map<String, Object> values = resolveValues(item, byId);

                List<Slide> filled = new ArrayList<>(template.size());
                for (Slide t : template) {
                    filled.add(DataFiller.fill(t, values));
                }

                String base = resolveFilenameBase(item, values, idx);
                List<NamedDocument> itemDocs = renderer.renderMulti(filled, base);
                docs.addAll(itemDocs);
                completed++;
            } catch (Exception e) {
                failed++;
                issues.add(new GenerationJob.ItemError(idx, e.getMessage()));
                log.warn("generation.item_failed user={} idx={} msg={}",
                        userId, idx, e.getMessage());
            }
            onItem.onItem(idx, completed, failed);
        }
        return new DocumentsResult(docs, issues);
    }

    public record DocumentsResult(
            List<NamedDocument> documents,
            List<GenerationJob.ItemError> issues
    ) { }

    @FunctionalInterface
    public interface ItemCallback {
        void onItem(int index, int completed, int failed);
    }

    /* ============================================================
       Упаковка
       ============================================================ */

    public PackResult pack(String packAs, String format, List<NamedDocument> docs) {
        if (docs == null || docs.isEmpty()) {
            throw ApiException.badRequest("No documents to pack");
        }
        String key = (packAs == null || packAs.isBlank()) ? "zip" : packAs.trim().toLowerCase();

        if ("single".equals(key)) {
            if (docs.size() != 1) {
                throw ApiException.badRequest(
                        "packAs=single requires exactly one document, got " + docs.size() +
                                ". Use packAs=zip or reduce selection."
                );
            }
            NamedDocument doc = docs.get(0);
            DocumentRenderer renderer = rendererFor(ExportFormat.fromString(format));
            return new PackResult(doc.content(), doc.filename(), renderer.mimeType());
        }

        DocumentPackager p = packagers.get(key);
        if (p == null) throw ApiException.badRequest("Unsupported packAs: " + key);
        return p.pack(docs);
    }

    public DocumentRenderer rendererFor(ExportFormat format) {
        DocumentRenderer r = renderers.get(format);
        if (r == null) throw ApiException.badRequest("Unsupported format: " + format);
        return r;
    }

    /* ============================================================
       Валидация / резолвинг
       ============================================================ */

    /**
     * templateId резолвится через TemplateRepository; inlineSlides — напрямую.
     * Приоритет: inlineSlides (для тестов / предпросмотра) → templateId.
     */
    private List<Slide> requireTemplate(GenerationRequest req, String userId) {
        if (req.inlineSlides != null && !req.inlineSlides.isEmpty()) {
            return req.inlineSlides;
        }
        if (req.templateId != null && !req.templateId.isBlank()) {
            TemplateEntity t = templates.findByIdAndUserId(req.templateId, userId)
                    .orElseThrow(() -> ApiException.notFound("Template"));
            if (t.getSlides() == null || t.getSlides().isEmpty()) {
                throw ApiException.badRequest("Template has no slides: " + req.templateId);
            }
            return t.getSlides();
        }
        throw ApiException.badRequest("Either templateId or inlineSlides required");
    }

    private Map<String, DataModelEntity> loadModels(String userId,
                                                    List<GenerationRequest.Item> items) {
        Set<String> ids = items.stream()
                .map(i -> i.dataModelId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, DataModelEntity> byId = new HashMap<>();
        if (!ids.isEmpty()) {
            for (DataModelEntity m : models.findAllByUserIdAndIdIn(userId, new ArrayList<>(ids))) {
                byId.put(m.getId(), m);
            }
        }
        return byId;
    }

    private Map<String, Object> resolveValues(GenerationRequest.Item item,
                                              Map<String, DataModelEntity> byId) {
        if (item.inlineValues != null && !item.inlineValues.isEmpty()) return item.inlineValues;
        if (item.dataModelId != null) {
            DataModelEntity m = byId.get(item.dataModelId);
            if (m == null) throw new IllegalArgumentException("DataModel not found: " + item.dataModelId);
            return m.getValues();
        }
        throw new IllegalArgumentException("Item has neither inlineValues nor dataModelId");
    }

    private String resolveFilenameBase(GenerationRequest.Item item,
                                       Map<String, Object> values,
                                       int idx) {
        String base;
        if (item.filenameOverride != null && !item.filenameOverride.isBlank()) {
            base = FilenameTemplate.resolve(item.filenameOverride, values, "document-" + (idx + 1));
        } else {
            base = FilenameTemplate.resolve(null, values, "document-" + (idx + 1));
        }
        return base.replaceAll("\\.(svg|pdf|pdfa\\.pdf)$", "");
    }
}