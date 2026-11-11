package ru.ticket.workflow;

import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.ticket.auth.security.UserIdPrincipal;

@RestController
public class WorkflowController {
  public record Save(WorkflowDefinition definition, Long revision) {}

  public record Publish(long revision, boolean shared) {}

  public record Run(long revision, Map<String, Object> values) {}

  private final WorkflowService service;

  @GetMapping("/api/workflows/availability")
  public Map<String, String> availability(@RequestParam String slug) {
    return Map.of("status", service.availability(slug));
  }

  public WorkflowController(WorkflowService service) {
    this.service = service;
  }

  @GetMapping("/api/workflows")
  public List<WorkflowService.Managed> list(@AuthenticationPrincipal UserIdPrincipal user) {
    return service.list(user.userId());
  }

  @GetMapping("/api/workflows/template-fields")
  public List<WorkflowDefinition.Field> fields(
      @AuthenticationPrincipal UserIdPrincipal user, @RequestParam String templateId) {
    return service.templateFields(user.userId(), templateId);
  }

  @PostMapping("/api/workflows")
  public WorkflowService.Managed save(
      @AuthenticationPrincipal UserIdPrincipal user, @RequestBody Save input) {
    if (input.definition() == null)
      throw ru.ticket.common.ApiException.badRequest("Укажите сценарий");
    return service.save(user.userId(), input.definition(), input.revision());
  }

  @PostMapping("/api/workflows/{slug}/publish")
  public WorkflowService.Managed publish(
      @AuthenticationPrincipal UserIdPrincipal user,
      @PathVariable String slug,
      @RequestBody Publish input) {
    return service.publish(user.userId(), slug, input.revision(), input.shared());
  }

  @DeleteMapping("/api/workflows/{slug}/publication")
  public void unpublish(@AuthenticationPrincipal UserIdPrincipal user, @PathVariable String slug) {
    service.unpublish(user.userId(), slug);
  }

  @GetMapping("/api/public/workflows")
  public List<WorkflowService.PublicView> catalog() {
    return service.catalog();
  }

  @GetMapping("/api/public/workflows/{slug}")
  public WorkflowService.PublicView get(@PathVariable String slug) {
    return service.publicView(slug);
  }

  @PostMapping("/api/public/workflows/{slug}/pdf")
  public ResponseEntity<byte[]> run(@PathVariable String slug, @RequestBody Run input) {
    var result = service.generate(slug, input.revision(), input.values());
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .cacheControl(CacheControl.noStore())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("document.pdf").build().toString())
        .body(result.content());
  }
}
