package ru.ticket.workflow;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "workflows", indexes = @Index(name = "ix_workflow_owner", columnList = "ownerId"))
public class WorkflowEntity {
  @Id
  @Column(length = 48)
  public String slug;

  @Column(nullable = false, length = 120)
  public String ownerId;

  @Column(nullable = false, columnDefinition = "TEXT")
  public String draft;

  @Column(columnDefinition = "TEXT")
  public String published;

  @Column(columnDefinition = "TEXT")
  public String source;

  public boolean shared;
  public long revision;
  public long publishedRevision;

  @Column(nullable = false)
  public Instant updatedAt;

  @Version public Long lockVersion;
}
