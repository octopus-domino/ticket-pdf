package ru.ticket.journey.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

/** Existing immutable route snapshot, retaining the v1 persistence mapping. */
@Entity
@Table(name = "journey_routes")
public class JourneyRoute {
  @Id
  @Column(length = 64)
  private String id;

  @Column(nullable = false, length = 750000)
  private String payload;

  private Instant createdAt;

  protected JourneyRoute() {}

  /** Creates a snapshot from the canonical serialized route. */
  public JourneyRoute(String id, String payload, Instant createdAt) {
    this.id = id;
    this.payload = payload;
    this.createdAt = createdAt;
  }

  /** Returns the canonical serialized route. */
  public String payload() {
    return payload;
  }
}
