package ru.ticket.journey;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "public_journeys")
public class JourneyEntity {
  @Id
  @Column(length = 36)
  public String id;

  @Column(nullable = false, length = 32)
  public String kind;

  @Column(nullable = false, length = 120)
  public String title;

  @Column(length = 36)
  public String ownerId;

  @Column(length = 48)
  public String siteSlug;

  @Column(nullable = false, length = 64)
  public String managementHash;

  @Column(nullable = false, columnDefinition = "TEXT")
  public String dataJson;

  @Column(nullable = false)
  public boolean revoked;

  @Column(nullable = false)
  public Instant createdAt;

  @Column(nullable = false)
  public Instant updatedAt;

  public Instant usedAt;
  @Version public Long version;
}
