package ru.ticket.workflow;

import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticket.common.ApiException;

@Service
public class WorkflowVisitService {
  public record Status(String id, String title, Instant issuedAt, Instant checkedAt, Instant visitedAt,
      boolean canCheck, String checkedBy, String note) {}
  public record Check(boolean visited, String note) {}
  private final WorkflowVisitRepository visits;
  private final String frontend;
  public WorkflowVisitService(WorkflowVisitRepository visits, @Value("${app.frontend-url:https://www.codoc.site}") String frontend) {
    this.visits = visits; this.frontend = frontend.replaceAll("/+$", "");
  }
  public WorkflowVisit draft(WorkflowEntity workflow, String title) {
    var visit = new WorkflowVisit(); visit.id = UUID.randomUUID().toString().replace("-", "");
    visit.ownerId = workflow.ownerId; visit.workflowSlug = workflow.slug; visit.workflowRevision = workflow.publishedRevision;
    visit.title = title; visit.issuedAt = Instant.now(); return visit;
  }
  public String url(WorkflowVisit visit) { return frontend + "/visits/" + visit.id; }
  @Transactional
  public void issued(WorkflowVisit visit, byte[] pdf) {
    try { visit.documentSha256 = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(pdf)); }
    catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    visits.save(visit);
  }
  @Transactional(readOnly = true)
  public Status status(String id, String owner) { return view(find(id, false), owner); }
  @Transactional
  public Status check(String id, String owner, Check check) {
    var visit = find(id, true);
    if (owner == null || !owner.equals(visit.ownerId)) throw ApiException.forbidden("Only the workflow owner can record attendance");
    if (check == null || check.note() != null && check.note().length() > 500) throw ApiException.badRequest("Invalid attendance check");
    // A repeated scan acknowledges the original mark; it never creates a second visit.
    if (visit.visitedAt == null) {
      if (visit.checkedAt == null) { visit.checkedAt = Instant.now(); visit.checkedBy = owner; }
      if (check.visited()) visit.visitedAt = Instant.now();
      visit.note = check.note() == null ? null : check.note().strip(); visits.save(visit);
    }
    return view(visit, owner);
  }
  private WorkflowVisit find(String id, boolean lock) {
    if (id == null || !id.matches("[a-f0-9]{32}")) throw ApiException.notFound("Visit");
    return (lock ? visits.lockById(id) : visits.findById(id)).orElseThrow(() -> ApiException.notFound("Visit"));
  }
  private static Status view(WorkflowVisit visit, String owner) {
    boolean allowed = owner != null && owner.equals(visit.ownerId);
    return new Status(visit.id, visit.title, visit.issuedAt, visit.checkedAt, visit.visitedAt, allowed,
        allowed ? visit.checkedBy : null, allowed ? visit.note : null);
  }
}
