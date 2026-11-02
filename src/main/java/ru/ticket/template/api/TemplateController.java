package ru.ticket.template.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.ticket.auth.security.UserIdPrincipal;
import ru.ticket.template.dto.TemplateDtos.RenameTemplateRequest;
import ru.ticket.template.dto.TemplateDtos.SaveTemplateRequest;
import ru.ticket.template.dto.TemplateDtos.TemplateDto;
import ru.ticket.template.dto.TemplateDtos.TemplateSummaryDto;
import ru.ticket.template.service.TemplateService;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final TemplateService service;

    public TemplateController(TemplateService service) {
        this.service = service;
    }

    @GetMapping
    public List<TemplateSummaryDto> list(
            @AuthenticationPrincipal UserIdPrincipal me,
            @RequestParam(name = "schemaId", required = false) String schemaId) {
        return service.list(me.userId(), schemaId);
    }

    @GetMapping("/{id}")
    public TemplateDto get(
            @AuthenticationPrincipal UserIdPrincipal me,
            @PathVariable String id) {
        return service.get(me.userId(), id);
    }

    @PostMapping
    public ResponseEntity<TemplateDto> create(
            @AuthenticationPrincipal UserIdPrincipal me,
            @Valid @RequestBody SaveTemplateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(me.userId(), req));
    }

    @PutMapping("/{id}")
    public TemplateDto update(
            @AuthenticationPrincipal UserIdPrincipal me,
            @PathVariable String id,
            @Valid @RequestBody SaveTemplateRequest req) {
        return service.update(me.userId(), id, req);
    }

    @PutMapping("/{id}/name")
    public TemplateDto rename(
            @AuthenticationPrincipal UserIdPrincipal me,
            @PathVariable String id,
            @Valid @RequestBody RenameTemplateRequest req) {
        return service.rename(me.userId(), id, req.name());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserIdPrincipal me,
            @PathVariable String id) {
        service.delete(me.userId(), id);
        return ResponseEntity.noContent().build();
    }
}