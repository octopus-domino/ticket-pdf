package ru.ticket.schema.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.ticket.auth.security.UserIdPrincipal;
import ru.ticket.schema.dto.SchemaDtos.*;
import ru.ticket.schema.service.SchemaService;

import java.util.List;

@RestController
@RequestMapping("/api/schemas")
public class SchemaController {

    private final SchemaService service;

    public SchemaController(SchemaService service) { this.service = service; }

    @GetMapping
    public List<SchemaDto> list(@AuthenticationPrincipal UserIdPrincipal me) {
        return service.list(me.userId());
    }

    @GetMapping("/{id}")
    public SchemaDto get(@AuthenticationPrincipal UserIdPrincipal me, @PathVariable String id) {
        return service.get(me.userId(), id);
    }

    @PostMapping
    public ResponseEntity<SchemaDto> create(@AuthenticationPrincipal UserIdPrincipal me,
                                            @Valid @RequestBody CreateSchemaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(me.userId(), req));
    }

    @PutMapping("/{id}")
    public SchemaDto update(@AuthenticationPrincipal UserIdPrincipal me,
                            @PathVariable String id,
                            @Valid @RequestBody UpdateSchemaRequest req) {
        return service.update(me.userId(), id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserIdPrincipal me, @PathVariable String id) {
        service.delete(me.userId(), id);
        return ResponseEntity.noContent().build();
    }
}