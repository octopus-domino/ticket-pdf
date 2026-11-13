package ru.ticket.journey;

import java.util.concurrent.Semaphore;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import ru.ticket.auth.security.UserIdPrincipal;
import ru.ticket.common.ApiException;
import ru.ticket.export.*;

/** One bounded PDF path for public documents and both ways of managing a release. */
@RestController
public class JourneyPdfController {
  private final JourneyService service;
  private final JourneyDocumentFactory documents;
  private final ExportFacade export;
  private final Semaphore preparation = new Semaphore(1);
  private final ru.ticket.generation.application.GenerationService generation;
  public JourneyPdfController(JourneyService service, JourneyDocumentFactory documents, ExportFacade export, ru.ticket.generation.application.GenerationService generation) {
    this.service = service; this.documents = documents; this.export = export; this.generation = generation;
  }
  @GetMapping("/api/journeys/batches/{id}/pdf")
  public ResponseEntity<byte[]> batch(@PathVariable String id, @RequestHeader(value = "X-Management-Key", required = false) String key) {
    return batchPdf(service.batch(id, key));
  }
  @GetMapping("/api/publications/batches/{id}/pdf")
  public ResponseEntity<byte[]> batchOwned(@AuthenticationPrincipal UserIdPrincipal user, @PathVariable String id) {
    return batchPdf(service.batchOwned(id, user.userId()));
  }
  @GetMapping("/api/journeys/batches/{id}/zip")
  public ResponseEntity<byte[]> zip(@PathVariable String id, @RequestHeader(value = "X-Management-Key", required = false) String key) {
    return zip(service.batch(id, key));
  }
  @GetMapping("/api/publications/batches/{id}/zip")
  public ResponseEntity<byte[]> zipOwned(@AuthenticationPrincipal UserIdPrincipal user, @PathVariable String id) {
    return zip(service.batchOwned(id, user.userId()));
  }
  private ResponseEntity<byte[]> zip(JourneyService.Batch batch) {
    acquire();
    try {
      var files = new java.util.ArrayList<ru.ticket.generation.application.dto.NamedDocument>();
      long bytes = 0, deadline = System.nanoTime() + java.time.Duration.ofMinutes(2).toNanos();
      for (var item : batch.documents()) if (!item.status().state().equals("REVOKED")) {
        if (System.nanoTime() > deadline) throw ApiException.badRequest("Release is too large; split the release");
        var pdf = export.export(ExportFormat.PDF, documents.document(item.document())); bytes += pdf.content().length;
        if (bytes > 32L * 1024 * 1024) throw ApiException.badRequest("Release exceeds 32 MiB; split the release");
        files.add(new ru.ticket.generation.application.dto.NamedDocument("codoc-" + item.document().id() + ".pdf", pdf.content()));
      }
      var result = generation.pack("zip", "pdf", files);
      return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip")).cacheControl(CacheControl.noStore())
          .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("codoc-release.zip").build().toString()).body(result.content());
    } finally { preparation.release(); }
  }
  private ResponseEntity<byte[]> batchPdf(JourneyService.Batch batch) {
    acquire();
    try {
      var values = batch.documents().stream().filter(item -> !item.status().state().equals("REVOKED")).map(JourneyService.Issued::document).toList();
      return response(export.export(ExportFormat.PDF, documents.batch(values)).content(), "codoc-release.pdf");
    } finally { preparation.release(); }
  }
  @GetMapping(value = "/api/journeys/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> document(@PathVariable String id, @RequestParam(defaultValue = "false") boolean archive) {
    acquire();
    long started=System.nanoTime();
    try {
      var view=service.get(id);var document=documents.document(view);long prepared=System.nanoTime();
      byte[] content=export.export(archive ? ExportFormat.PDFA : ExportFormat.PDF, document).content();
      org.slf4j.LoggerFactory.getLogger(getClass()).info("journey.pdf id={} kind={} prepare_ms={} render_ms={} bytes={}",
          id,view.kind(),(prepared-started)/1_000_000,(System.nanoTime()-prepared)/1_000_000,content.length);
      return response(content, "codoc-" + id + ".pdf");
    }
    finally { preparation.release(); }
  }
  private void acquire() {
    if (!preparation.tryAcquire()) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Another PDF is being prepared. Please retry shortly.");
  }
  private static ResponseEntity<byte[]> response(byte[] content, String filename) {
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString()).body(content);
  }
}
