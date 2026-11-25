package ru.ticket.datamodel.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.ticket.auth.security.UserIdPrincipal;
import ru.ticket.datamodel.dto.DataModelDtos.*;
import ru.ticket.datamodel.service.DataModelService;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/data-models")
public class DataModelController {

    private final DataModelService service;

    public DataModelController(DataModelService service) { this.service = service; }

    @GetMapping
    public List<ModelDto> list(@AuthenticationPrincipal UserIdPrincipal me,
                               @RequestParam(name = "schemaId", required = false) String schemaId) {
        return service.list(me.userId(), schemaId);
    }

    @GetMapping("/{id}")
    public ModelDto get(@AuthenticationPrincipal UserIdPrincipal me, @PathVariable String id) {
        return service.get(me.userId(), id);
    }

    @PostMapping
    public ResponseEntity<ModelDto> create(@AuthenticationPrincipal UserIdPrincipal me,
                                           @Valid @RequestBody CreateModelRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(me.userId(), req));
    }

    @PutMapping("/{id}")
    public ModelDto update(@AuthenticationPrincipal UserIdPrincipal me, @PathVariable String id,
                           @Valid @RequestBody UpdateModelRequest req) {
        return service.update(me.userId(), id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserIdPrincipal me, @PathVariable String id) {
        service.delete(me.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/import")
    public ImportResult importBulk(@AuthenticationPrincipal UserIdPrincipal me,
                                   @Valid @RequestBody ImportRequest req) {
        return service.importBulk(me.userId(), req);
    }

    @GetMapping("/export")
    public ExportBundle export(@AuthenticationPrincipal UserIdPrincipal me, @RequestParam String ids) {
        List<String> idList = Arrays.stream(ids.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return service.export(me.userId(), idList);
    }
}