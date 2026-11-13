package ru.ticket.workflow;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import ru.ticket.auth.security.UserIdPrincipal;

@RestController
public class WorkflowVisitController {
  private final WorkflowVisitService visits;
  public WorkflowVisitController(WorkflowVisitService visits) { this.visits = visits; }
  @GetMapping("/api/public/workflows/visits/{id}")
  public WorkflowVisitService.Status status(@PathVariable String id, @AuthenticationPrincipal UserIdPrincipal user) {
    return visits.status(id, user == null ? null : user.userId());
  }
  @PostMapping("/api/workflows/visits/{id}/check")
  public WorkflowVisitService.Status check(@PathVariable String id, @AuthenticationPrincipal UserIdPrincipal user,
      @RequestBody WorkflowVisitService.Check check) {
    return visits.check(id, user.userId(), check);
  }
}
