package ru.ticket.generation.api;

import jakarta.validation.Valid;
import java.util.Map;
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

@RestController
@RequestMapping("/api/generate")
public class GenerationController {

  private final GenerationService syncService;
  private final GenerationJobService jobService;
  private final JobFileStorage fileStorage;

  public GenerationController(
      GenerationService syncService, GenerationJobService jobService, JobFileStorage fileStorage) {
    this.syncService = syncService;
    this.jobService = jobService;
    this.fileStorage = fileStorage;
  }

  @PostMapping("/sync")
  public ResponseEntity<?> generateSync(
      @AuthenticationPrincipal UserIdPrincipal me, @Valid @RequestBody GenerationRequest req) {
    if (req.items.size() > 100) {
      return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
          .body(
              Map.of("error", "Batch size exceeds 100, use /api/generate (async)", "status", 413));
    }
    GenerationService.SyncResult r = syncService.generateSync(me.userId(), req);
    return bytesResponse(r.packed(), r.count(), r.issues());
  }

  @PostMapping
  public ResponseEntity<?> generateAsync(
      @AuthenticationPrincipal UserIdPrincipal me, @Valid @RequestBody GenerationRequest req) {
    GenerationJob job = jobService.enqueue(me.userId(), req);
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            Map.of(
                "jobId", job.id(),
                "status", job.status().name().toLowerCase(),
                "total", job.total(),
                "createdAt", job.createdAt().toString()));
  }

  @GetMapping("/{jobId}")
  public ResponseEntity<?> getJob(
      @AuthenticationPrincipal UserIdPrincipal me, @PathVariable String jobId) {
    GenerationJob j = jobService.find(me.userId(), jobId);
    Map<String, Object> response = new java.util.LinkedHashMap<>();
    response.put("jobId", j.id());
    response.put("status", j.status().name().toLowerCase());
    response.put(
        "progress", Map.of("total", j.total(), "completed", j.completed(), "failed", j.failed()));
    response.put("errors", j.errors());
    response.put(
        "resultUrl", j.resultPath() == null ? null : "/api/generate/" + j.id() + "/result");
    response.put("expiresAt", j.expiresAt().toString());
    return ResponseEntity.ok(response);
  }

  @GetMapping("/{jobId}/result")
  public ResponseEntity<byte[]> downloadResult(
      @AuthenticationPrincipal UserIdPrincipal me, @PathVariable String jobId) {
    GenerationJob j = jobService.find(me.userId(), jobId);
    if (j.resultPath() == null) return ResponseEntity.status(HttpStatus.CONFLICT).build();
    byte[] content = fileStorage.read(jobId);
    String filename = fileStorage.filename(jobId);
    MediaType type =
        filename.endsWith(".zip")
            ? MediaType.parseMediaType("application/zip")
            : filename.endsWith(".svg")
                ? MediaType.parseMediaType("image/svg+xml")
                : MediaType.APPLICATION_PDF;
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .contentType(type)
        .body(content);
  }

  @DeleteMapping("/{jobId}")
  public ResponseEntity<Void> cancel(
      @AuthenticationPrincipal UserIdPrincipal me, @PathVariable String jobId) {
    jobService.cancel(me.userId(), jobId);
    return ResponseEntity.noContent().build();
  }

  private String errorsJson(java.util.List<GenerationJob.ItemError> issues) {
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper()
          .writeValueAsString(
              issues.stream()
                  .limit(10)
                  .map(
                      issue ->
                          new GenerationJob.ItemError(
                              issue.index(),
                              issue.message() == null
                                  ? "Rendering failed"
                                  : issue
                                      .message()
                                      .substring(0, Math.min(180, issue.message().length()))))
                  .toList());
    } catch (Exception error) {
      return "[]";
    }
  }

  private ResponseEntity<byte[]> bytesResponse(
      PackResult packed, int count, java.util.List<GenerationJob.ItemError> issues) {
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + packed.filename() + "\"")
        .header(
            "X-Generation-Errors",
            java.util.Base64.getEncoder()
                .encodeToString(
                    errorsJson(issues).getBytes(java.nio.charset.StandardCharsets.UTF_8)))
        .header("X-Generation-Count", String.valueOf(count))
        .header("X-Generation-Failed", String.valueOf(issues.size()))
        .contentType(MediaType.parseMediaType(packed.mimeType()))
        .body(packed.content());
  }
}
