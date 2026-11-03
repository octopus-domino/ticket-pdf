package ru.ticket.email;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.ticket.auth.security.UserIdPrincipal;
import ru.ticket.common.ApiException;
import ru.ticket.dto.Slide;
import ru.ticket.email.EmailDtos.EmailRequest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/email")
public class EmailController {

    private final EmailService service;

    public EmailController(EmailService service) {
        this.service = service;
    }

    /**
     * Превью: один слайд → HTML с data URL картинками.
     * Открывается в iframe браузера, штампы и QR видны.
     */
    @PostMapping(value = "/preview", produces = "text/html")
    public ResponseEntity<String> preview(@RequestBody Map<String, Slide> body) throws Exception {
        Slide slide = body.get("slide");
        if (slide == null) throw ApiException.badRequest("slide required");
        String html = service.previewHtml(slide);
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(html);
    }

    /**
     * Скачать .eml: MIME-сообщение, открывается в Outlook двойным кликом.
     * Если items содержит несколько получателей — возвращает ZIP с .eml файлами.
     */
    @PostMapping("/build")
    public ResponseEntity<byte[]> build(@AuthenticationPrincipal UserIdPrincipal me,
                                        @Valid @RequestBody EmailRequest req) {
        List<EmailService.EmlFile> files = service.buildEmls(me.userId(), req);
        if (files.isEmpty()) throw ApiException.badRequest("No emails built");
        if (files.size() == 1) {
            EmailService.EmlFile f = files.get(0);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + f.filename() + "\"")
                    .contentType(MediaType.parseMediaType("message/rfc822"))
                    .body(f.content());
        }
        // несколько — ZIP
        byte[] zip = zipEmls(files);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"emails.zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(zip);
    }

    /**
     * Отправить по SMTP. Требует настроенного spring.mail.host.
     * Возвращает 202 при успехе (даже если 1+ писем).
     */
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> send(@AuthenticationPrincipal UserIdPrincipal me,
                                                    @Valid @RequestBody EmailRequest req) {
        try {
            service.sendAll(me.userId(), req);
            return ResponseEntity.accepted().body(Map.of(
                    "sent", req.items.size(),
                    "status", "ok"
            ));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("SMTP send failed: " + e.getMessage());
        }
    }

    private byte[] zipEmls(List<EmailService.EmlFile> files) {
        try (var out = new java.io.ByteArrayOutputStream();
             var zip = new java.util.zip.ZipOutputStream(out)) {
            for (EmailService.EmlFile f : files) {
                zip.putNextEntry(new java.util.zip.ZipEntry(f.filename()));
                zip.write(f.content());
                zip.closeEntry();
            }
            zip.finish();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("ZIP failed: " + e.getMessage(), e);
        }
    }
}