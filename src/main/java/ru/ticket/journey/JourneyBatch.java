package ru.ticket.journey;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Private issuance registry. Its secret is shared by the documents in one release. */
@Entity
@Table(name = "journey_batches")
public class JourneyBatch {
  @Id
  @Column(length = 36)
  public String id;

  @Column(nullable = false, length = 120)
  public String title;

  @Column(nullable = false, length = 32)
  public String kind;

  @Column(nullable = false, length = 64)
  public String managementHash;

  @Column(nullable = false, length = 5000)
  public String documentIds;

  @Column(nullable = false)
  public Instant createdAt;

  @jakarta.persistence.Version public Long version;
}
