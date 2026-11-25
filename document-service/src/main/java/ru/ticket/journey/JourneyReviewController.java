package ru.ticket.journey;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.ticket.auth.security.UserIdPrincipal;

@RestController
@RequestMapping("/api/journeys/{id}/reviews")
public class JourneyReviewController {
  private final JourneyReviewService service;

  public JourneyReviewController(JourneyReviewService service) {
    this.service = service;
  }

  @PostMapping
  public void submit(@PathVariable String id, @RequestBody JourneyReviewService.Input input) {
    service.submit(id, input);
  }

  @GetMapping
  public JourneyReviewService.Results list(
      @PathVariable String id,
      @RequestHeader(value = "X-Management-Key", required = false) String key,
      @AuthenticationPrincipal UserIdPrincipal user,
      @RequestParam(defaultValue = "0") int page) {
    return service.list(id, key, user == null ? null : user.userId(), page);
  }
}
