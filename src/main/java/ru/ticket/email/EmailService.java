package ru.ticket.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.ticket.common.ApiException;
import ru.ticket.datamodel.domain.DataModelEntity;
import ru.ticket.datamodel.repository.DataModelRepository;
import ru.ticket.dto.Slide;
import ru.ticket.email.EmailDtos.EmailItem;
import ru.ticket.email.EmailDtos.EmailRequest;
import ru.ticket.email.render.RenderMode;
import ru.ticket.email.render.SlideHtmlRenderer;
import ru.ticket.generation.domain.DataFiller;
import ru.ticket.generation.domain.FilenameTemplate;
import ru.ticket.template.domain.TemplateEntity;
import ru.ticket.template.repository.TemplateRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final TemplateRepository templates;
    private final DataModelRepository models;
    private final SlideHtmlRenderer slideRenderer;
    private final EmlBuilder emlBuilder;
    private final EmailSender sender;

    public EmailService(TemplateRepository templates,
                        DataModelRepository models,
                        SlideHtmlRenderer slideRenderer,
                        EmlBuilder emlBuilder,
                        EmailSender sender) {
        this.templates = templates;
        this.models = models;
        this.slideRenderer = slideRenderer;
        this.emlBuilder = emlBuilder;
        this.sender = sender;
    }

    /* ============================================================
       Preview — used by the email-preview modal (iframe).
       ============================================================ */

    public String previewHtml(Slide slide) {
        return slideRenderer.render(slide, RenderMode.PREVIEW).html();
    }

    /* ============================================================
       Build .eml (download path)
       ============================================================ */

    public record EmlFile(String filename, byte[] content) { }

    public List<EmlFile> buildEmls(String userId, EmailRequest req) {
        List<Slide> template = requireTemplate(req, userId);
        List<EmlFile> out = new ArrayList<>();
        Map<String, DataModelEntity> byId = loadModels(userId, req.items);

        for (int idx = 0; idx < req.items.size(); idx++) {
            EmailItem item = req.items.get(idx);
            try {
                Map<String, Object> values = resolveValues(item, byId);
                Slide filled = DataFiller.fill(template.get(0), values);

                SlideHtmlRenderer.Result rendered =
                        slideRenderer.render(filled, RenderMode.EMAIL);

                String subject = resolveSubject(item, values, req.email);
                String from = req.email != null && req.email.from() != null
                        ? req.email.from()
                        : "noreply@ticket-editor.local";

                byte[] eml = emlBuilder.build(
                        subject,
                        from,
                        req.email != null ? req.email.replyTo() : null,
                        rendered);

                String base = resolveFilename(item, values, idx);
                out.add(new EmlFile(base + ".eml", eml));
            } catch (Exception e) {
                log.warn("email.build.failed idx={} msg={}", idx, e.getMessage());
                throw ApiException.badRequest(
                        "Item #" + idx + " failed: " + e.getMessage());
            }
        }
        return out;
    }

    /* ============================================================
       Send via SMTP
       ============================================================ */

    public void sendAll(String userId, EmailRequest req) {
        List<Slide> template = requireTemplate(req, userId);
        Map<String, DataModelEntity> byId = loadModels(userId, req.items);

        int ok = 0;
        for (int idx = 0; idx < req.items.size(); idx++) {
            EmailItem item = req.items.get(idx);
            try {
                Map<String, Object> values = resolveValues(item, byId);
                Slide filled = DataFiller.fill(template.get(0), values);

                SlideHtmlRenderer.Result rendered =
                        slideRenderer.render(filled, RenderMode.EMAIL);

                String subject = resolveSubject(item, values, req.email);
                sender.send(item, req.email, subject, rendered);
                ok++;
            } catch (Exception e) {
                log.warn("email.send.failed idx={} to={} msg={}",
                        idx, item.to, e.getMessage());
                throw ApiException.badRequest(
                        "Item #" + idx + " to " + item.to + " failed: " + e.getMessage());
            }
        }
        log.info("email.send.batch user={} ok={}", userId, ok);
    }

    /* ============================================================
       Helpers — unchanged semantics
       ============================================================ */

    private List<Slide> requireTemplate(EmailRequest req, String userId) {
        if (req.inlineSlides != null && !req.inlineSlides.isEmpty()) {
            return req.inlineSlides;
        }
        if (req.templateId != null && !req.templateId.isBlank()) {
            TemplateEntity t = templates.findByIdAndUserId(req.templateId, userId)
                    .orElseThrow(() -> ApiException.notFound("Template"));
            if (t.getSlides() == null || t.getSlides().isEmpty()) {
                throw ApiException.badRequest("Template has no slides");
            }
            return t.getSlides();
        }
        throw ApiException.badRequest("Either inlineSlides or templateId required");
    }

    private Map<String, DataModelEntity> loadModels(String userId, List<EmailItem> items) {
        Set<String> ids = new HashSet<>();
        for (EmailItem it : items) {
            if (it.dataModelId != null) ids.add(it.dataModelId);
        }
        if (ids.isEmpty()) return Map.of();
        Map<String, DataModelEntity> out = new HashMap<>();
        for (DataModelEntity m : models.findAllByUserIdAndIdIn(userId, new ArrayList<>(ids))) {
            out.put(m.getId(), m);
        }
        return out;
    }

    private Map<String, Object> resolveValues(EmailItem item,
                                              Map<String, DataModelEntity> byId) {
        if (item.inlineValues != null) return item.inlineValues;
        if (item.dataModelId != null) {
            DataModelEntity m = byId.get(item.dataModelId);
            if (m == null) {
                throw new IllegalArgumentException(
                        "DataModel not found: " + item.dataModelId);
            }
            return m.getValues();
        }
        throw new IllegalArgumentException(
                "Item has neither inlineValues nor dataModelId");
    }

    private String resolveSubject(EmailItem item,
                                  Map<String, Object> values,
                                  EmailDtos.EmailOptions opts) {
        String raw = item.subject != null && !item.subject.isBlank()
                ? item.subject
                : (opts != null && opts.subject() != null && !opts.subject().isBlank()
                ? opts.subject()
                : "Документ");
        return FilenameTemplate.resolve(raw, values, "Документ");
    }

    private String resolveFilename(EmailItem item,
                                   Map<String, Object> values,
                                   int idx) {
        String base = item.filenameOverride != null && !item.filenameOverride.isBlank()
                ? FilenameTemplate.resolve(item.filenameOverride, values,
                "email-" + (idx + 1))
                : "email-" + (idx + 1);
        return base.replaceAll("\\.(eml|html|pdf)$", "");
    }
}