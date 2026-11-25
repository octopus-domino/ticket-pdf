package ru.ticket.journey;

import java.util.List;
import java.util.concurrent.Semaphore;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import ru.ticket.common.ApiException;
import ru.ticket.export.*;

@RestController
@RequestMapping("/api/journeys")
public class JourneyController {
  private final JourneyService service;
  private final JourneyAttachments attachments;

  public JourneyController(
      JourneyService service,
      JourneyAttachments attachments) {
    this.service = service;
    this.attachments = attachments;
  }

  @PostMapping
  public List<JourneyService.Created> create(
      @RequestBody JourneyService.Input input,
      @org.springframework.security.core.annotation.AuthenticationPrincipal
          ru.ticket.auth.security.UserIdPrincipal user) {
    return service.create(input, user == null ? null : user.userId());
  }

  @GetMapping("/{id}")
  public JourneyService.View get(@PathVariable String id) {
    return service.get(id);
  }

  @GetMapping("/{id}/manage")
  public JourneyService.View manage(
      @PathVariable String id,
      @RequestHeader(value = "X-Management-Key", required = false) String key) {
    return service.manage(id, key);
  }

  @PutMapping("/{id}")
  public JourneyService.View update(
      @PathVariable String id,
      @RequestHeader(value = "X-Management-Key", required = false) String key,
      @RequestBody JourneyService.Input input) {
    return service.update(id, key, input);
  }

  @DeleteMapping("/{id}")
  public void revoke(
      @PathVariable String id,
      @RequestHeader(value = "X-Management-Key", required = false) String key) {
    service.revoke(id, key);
  }

  @PostMapping("/{id}/responses")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void respond(@PathVariable String id, @RequestBody JourneyService.Reply reply) {
    service.respond(id, reply);
  }

  @GetMapping("/{id}/responses")
  public List<JourneyService.Guest> responses(
      @PathVariable String id,
      @RequestHeader(value = "X-Management-Key", required = false) String key) {
    return service.guests(id, key);
  }

  @GetMapping("/{id}/status")
  public JourneyContentPolicy.Status status(@PathVariable String id) {
    return service.status(id);
  }

  @PostMapping("/{id}/consume")
  public JourneyContentPolicy.Status consume(
      @PathVariable String id,
      @RequestHeader(value = "X-Management-Key", required = false) String key) {
    return service.consume(id, key);
  }

  @GetMapping("/batches/{id}")
  public JourneyService.Batch batch(
      @PathVariable String id,
      @RequestHeader(value = "X-Management-Key", required = false) String key) {
    return service.batch(id, key);
  }
  @PostMapping("/{id}/status")
  public JourneyContentPolicy.Status changeStatus(@PathVariable String id,
      @RequestHeader(value = "X-Management-Key", required = false) String key, @RequestBody JourneyService.StatusChange change) {
    return service.changeStatus(id, key, null, change);
  }

  @GetMapping("/{id}/calendar")
  public ResponseEntity<String> calendar(@PathVariable String id) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, "text/calendar; charset=utf-8")
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"schedule.ics\"")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(attachments.calendar(service.get(id)));
  }

  @GetMapping("/{id}/contact")
  public ResponseEntity<String> contact(@PathVariable String id) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, "text/vcard; charset=utf-8")
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"contact.vcf\"")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(attachments.contact(service.get(id)));
  }

}
