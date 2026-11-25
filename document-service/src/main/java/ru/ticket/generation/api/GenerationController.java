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
    var source = syncService.freezeSource(me.userId(), req);
    var docs = syncService.generateDocuments(me.userId(), req, (i,c,f) -> {}, source);
    if (docs.documents().isEmpty()) throw ru.ticket.common.ApiException.badRequest("All items failed: " + docs.issues());
    var r = new GenerationService.SyncResult(syncService.pack(req.packAs,req.format,docs.documents()), req.items.size()-docs.issues().size(),docs.issues());
    String historyId="unavailable";
    try { historyId=jobService.archiveSync(me.userId(), req, source, r, fileStorage).id(); }
    catch (RuntimeException failure) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("generation.history_unavailable"); }
    var response=bytesResponse(r.packed(), r.count(), r.issues());
    var headers=new HttpHeaders();headers.putAll(response.getHeaders());headers.set("X-Generation-History",historyId);
    return new ResponseEntity<>(response.getBody(),headers,response.getStatusCode());
  }

  @PostMapping("/preview")
  public ResponseEntity<?> preview(@AuthenticationPrincipal UserIdPrincipal me,
      @Valid @RequestBody GenerationRequest req,@RequestParam(defaultValue="0") int page) {
    if (req.items.size() != 1 || !"svg".equals(req.format) || !"single".equals(req.packAs))
      throw ru.ticket.common.ApiException.badRequest("Preview requires one item, SVG and single packaging");
    var source=syncService.freezeSource(me.userId(),req);
    int pages=source.template().slides().size();
    if(page<0||page>=pages)throw ru.ticket.common.ApiException.badRequest("Invalid preview page");
    var template=source.template();
    var single=new ru.ticket.generation.application.dto.GenerationSource(new ru.ticket.generation.application.port.GenerationCatalog.Template(
        java.util.List.of(template.slides().get(page)),template.schemaId(),template.fields(),template.bindings()),source.models());
    var result=syncService.generateDocuments(me.userId(),req,(i,c,f)->{},single);
    if(result.documents().isEmpty())throw ru.ticket.common.ApiException.badRequest("Preview failed: "+result.issues());
    return ResponseEntity.ok().contentType(MediaType.valueOf("image/svg+xml"))
        .header("Cache-Control", "no-store").header("X-Preview-Pages",String.valueOf(pages)).body(result.documents().get(0).content());
  }

  @PostMapping
  public ResponseEntity<?> generateAsync(
      @AuthenticationPrincipal UserIdPrincipal me, @Valid @RequestBody GenerationRequest req) {
    GenerationJob job = jobService.enqueue(me.userId(), req, syncService.freezeSource(me.userId(), req));
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

  @GetMapping
  public java.util.List<Map<String,Object>> history(@AuthenticationPrincipal UserIdPrincipal me) {
    return jobService.listByUser(me.userId()).stream().filter(job -> job.expiresAt().isAfter(java.time.Instant.now()))
        .sorted(java.util.Comparator.comparing(GenerationJob::createdAt).reversed()).limit(100)
        .map(job -> Map.<String,Object>of("jobId",job.id(),"status",job.status().name().toLowerCase(),
          "createdAt",job.createdAt().toString(),"expiresAt",job.expiresAt().toString(),"total",job.total(),
          "completed",job.completed(),"failed",job.failed(),"format",job.request().format,"hasSnapshot",job.source()!=null)).toList();
  }

  @PostMapping("/{jobId}/retry")
  public Map<String,Object> retry(@AuthenticationPrincipal UserIdPrincipal me,@PathVariable String jobId) {
    var previous=jobService.find(me.userId(),jobId);
    if (!java.util.Set.of(GenerationJob.Status.DONE,GenerationJob.Status.FAILED).contains(previous.status()))
      throw ru.ticket.common.ApiException.conflict("Wait for the original job to finish");
    var indices=previous.errors().stream().map(GenerationJob.ItemError::index).filter(index->index>=0).distinct().toList();
    if(indices.isEmpty())throw ru.ticket.common.ApiException.badRequest("No failed items to retry");
    var request=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().convertValue(previous.request(),GenerationRequest.class);
    request.items=indices.stream().map(index->request.items.get(index)).toList();
    var source=previous.source()==null?syncService.freezeSource(me.userId(),request):previous.source();
    var next=jobService.enqueue(me.userId(),request,source);
    return Map.of("jobId",next.id(),"total",next.total());
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
    return bytesResponse(new PackResult(content,filename,type.toString()),j.completed(),j.errors());
  }

  @DeleteMapping("/{jobId}")
  public ResponseEntity<Void> cancel(
      @AuthenticationPrincipal UserIdPrincipal me, @PathVariable String jobId) {
    jobService.cancel(me.userId(), jobId);
    return ResponseEntity.noContent().build();
  }

  private static String errorsJson(java.util.List<GenerationJob.ItemError> issues) {
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

  static ResponseEntity<byte[]> bytesResponse(
      PackResult packed, int count, java.util.List<GenerationJob.ItemError> issues) {
    return ResponseEntity.ok()
        .header("Cache-Control","no-store")
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
