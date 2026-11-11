package ru.ticket.journey;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "journey_sites")
public class JourneySite {
  @Id
  @Column(length = 48)
  public String slug;

  @Column(nullable = false, columnDefinition = "TEXT")
  public String definition;

  @Column(nullable = false, length = 64)
  public String managementHash;

  @Column(nullable = false)
  public Instant updatedAt;

  @Version public Long version;
}
