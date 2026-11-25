package ru.ticket.workflow;

import jakarta.persistence.*;
import java.time.Instant;

/** An issued verification record stores no form answers or sensor data. */
@Entity
@Table(name = "workflow_visits")
public class WorkflowVisit {
  @Id @Column(length = 32) public String id;
  @Column(nullable = false, length = 120) public String ownerId;
  @Column(nullable = false, length = 48) public String workflowSlug;
  public long workflowRevision;
  @Column(nullable = false, length = 120) public String title;
  @Column(nullable = false) public Instant issuedAt;
  @Column(nullable = false, length = 64) public String documentSha256;
  public Instant checkedAt;
  public Instant visitedAt;
  @Column(length = 120) public String checkedBy;
  @Column(length = 500) public String note;
  @Version public Long version;
}
