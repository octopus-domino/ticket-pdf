package ru.ticket.generation.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.ticket.auth.security.UserIdPrincipal;
import ru.ticket.generation.application.GenerationJobService;
import ru.ticket.generation.application.GenerationService;
import ru.ticket.generation.application.JobFileStorage;
import ru.ticket.generation.application.dto.GenerationJob;
import ru.ticket.generation.application.dto.GenerationRequest;
import ru.ticket.generation.application.dto.PackResult;

import java.util.Map;

@RestController
@RequestMapping("/api/generate")
public class GenerationController {

    private final GenerationService syncService;
    private final GenerationJobService jobService;
    private final JobFileStorage fileStorage;

    public GenerationController(GenerationService syncService,
                                GenerationJobService jobService,
                                JobFileStorage fileStorage) {
        this.syncService = syncService;
        this.jobService = jobService;
        this.fileStorage = fileStorage;
    }

    @PostMapping("/sync")
    public ResponseEntity<byte[]> generateSync(@AuthenticationPrincipal UserIdPrincipal me,
                                               @Valid @RequestBody GenerationRequest req) {
        if (req.items.size() > 100) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body("Batch size exceeds 100, use /api/generate (async)".getBytes());
        }
        GenerationService.SyncResult r = syncService.generateSync(me.userId(), req);
        return bytesResponse(r.packed(), r.count(), r.issues().size());
    }

    @PostMapping
    public ResponseEntity<?> generateAsync(@AuthenticationPrincipal UserIdPrincipal me,
                                           @Valid @RequestBody GenerationRequest req) {
        GenerationJob job = jobService.enqueue(me.userId(), req);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "jobId", job.id(),
                "status", job.status().name().toLowerCase(),
                "total", job.total(),
                "createdAt", job.createdAt().toString()
        ));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<?> getJob(@AuthenticationPrincipal UserIdPrincipal me, @PathVariable String jobId) {
        GenerationJob j = jobService.find(me.userId(), jobId);
        return ResponseEntity.ok(Map.of(
                "jobId", j.id(),
                "status", j.status().name().toLowerCase(),
                "progress", Map.of("total", j.total(), "completed", j.completed(), "failed", j.failed()),
                "errors", j.errors(),
                "resultUrl", j.resultPath() == null ? null : "/api/generate/" + j.id() + "/result",
                "expiresAt", j.expiresAt().toString()
        ));
    }

    @GetMapping("/{jobId}/result")
    public ResponseEntity<byte[]> downloadResult(@AuthenticationPrincipal UserIdPrincipal me,
                                                 @PathVariable String jobId) {
        GenerationJob j = jobService.find(me.userId(), jobId);
        if (j.resultPath() == null) return ResponseEntity.status(HttpStatus.CONFLICT).build();
        byte[] content = fileStorage.read(jobId);
        String filename = fileStorage.filename(jobId);
        MediaType type = filename.endsWith(".zip")
                ? MediaType.parseMediaType("application/zip")
                : MediaType.APPLICATION_PDF;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(type)
                .body(content);
    }

    @DeleteMapping("/{jobId}")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal UserIdPrincipal me,
                                       @PathVariable String jobId) {
        jobService.cancel(me.userId(), jobId);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<byte[]> bytesResponse(PackResult packed, int count, int failed) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + packed.filename() + "\"")
                .header("X-Generation-Count", String.valueOf(count))
                .header("X-Generation-Failed", String.valueOf(failed))
                .contentType(MediaType.parseMediaType(packed.mimeType()))
                .body(packed.content());
    }
}