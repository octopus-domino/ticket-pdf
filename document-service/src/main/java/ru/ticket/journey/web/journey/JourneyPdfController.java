package ru.ticket.journey.web.journey;

import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.ticket.auth.security.UserIdPrincipal;
import ru.ticket.journey.application.journey.JourneyService;
import ru.ticket.journey.application.journey.JourneyPrintService;

/** Exposes published document and release exports. */
@RestController
public class JourneyPdfController {
  private final JourneyService service;
  private final JourneyPrintService printing;

  public JourneyPdfController(JourneyService service, JourneyPrintService printing) {
    this.service = service;
    this.printing = printing;
  }

  @GetMapping("/api/journeys/batches/{id}/pdf")
  public ResponseEntity<byte[]> batch(@PathVariable String id, @RequestHeader(value = "X-Management-Key", required = false) String key) {
    return pdf(printing.batchPdf(service.batch(id, key)), "codoc-release.pdf");
  }
  @GetMapping("/api/publications/batches/{id}/pdf")
  public ResponseEntity<byte[]> batchOwned(@AuthenticationPrincipal UserIdPrincipal user, @PathVariable String id) {
    return pdf(printing.batchPdf(service.batchOwned(id, user.userId())), "codoc-release.pdf");
  }
  @GetMapping("/api/journeys/batches/{id}/zip")
  public ResponseEntity<byte[]> zip(@PathVariable String id, @RequestHeader(value = "X-Management-Key", required = false) String key) {
    return zip(printing.zip(service.batch(id, key)));
  }
  @GetMapping("/api/publications/batches/{id}/zip")
  public ResponseEntity<byte[]> zipOwned(@AuthenticationPrincipal UserIdPrincipal user, @PathVariable String id) {
    return zip(printing.zip(service.batchOwned(id, user.userId())));
  }
  @GetMapping(value = "/api/journeys/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> document(@PathVariable String id, @RequestParam(defaultValue = "false") boolean archive) {
    return pdf(printing.document(id, archive), "codoc-" + id + ".pdf");
  }

  private static ResponseEntity<byte[]> pdf(byte[] content, String filename) {
    return attachment(content, filename, MediaType.APPLICATION_PDF);
  }

  private static ResponseEntity<byte[]> zip(byte[] content) {
    return attachment(content, "codoc-release.zip", MediaType.parseMediaType("application/zip"));
  }

  private static ResponseEntity<byte[]> attachment(byte[] content, String filename, MediaType type) {
    return ResponseEntity.ok().contentType(type).cacheControl(CacheControl.noStore())
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
        .body(content);
  }
}
