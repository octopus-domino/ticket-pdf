package ru.ticket.journey;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "journey_reviews",
    uniqueConstraints = @UniqueConstraint(columnNames = {"journey_id", "visitor_hash"}),
    indexes = @Index(name = "idx_journey_reviews_date", columnList = "journey_id,created_at"))
public class JourneyReview {
  @Id
  @Column(length = 36)
  String id;

  @Column(name = "journey_id", nullable = false, length = 36)
  String journeyId;

  @Column(name = "visitor_hash", nullable = false, length = 64)
  String visitorHash;

  int rating;

  @Column(nullable = false, length = 2000)
  String message;

  @Column(name = "created_at", nullable = false)
  Instant createdAt;
}
