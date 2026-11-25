package ru.ticket.journey;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "journey_responses",
    uniqueConstraints = @UniqueConstraint(columnNames = {"journey_id", "response_hash"}))
public class JourneyResponse {
  @Id
  @Column(length = 36)
  public String id;

  @Column(name = "journey_id", nullable = false, length = 36)
  public String journeyId;

  @Column(name = "response_hash", nullable = false, length = 64)
  public String responseHash;

  @Column(nullable = false, length = 100)
  public String name;

  @Column(nullable = false)
  public boolean attending;

  @Column(nullable = false)
  public Instant updatedAt;
}
