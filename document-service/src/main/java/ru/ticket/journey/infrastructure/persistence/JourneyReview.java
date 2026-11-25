package ru.ticket.journey.infrastructure.persistence;

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
  private String id;

  @Column(name = "journey_id", nullable = false, length = 36)
  private String journeyId;

  @Column(name = "visitor_hash", nullable = false, length = 64)
  private String visitorHash;

  private int rating;

  @Column(nullable = false, length = 2000)
  private String message;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected JourneyReview() {}

  public JourneyReview(String id, String journeyId, String visitorHash) {
    this.id = id;
    this.journeyId = journeyId;
    this.visitorHash = visitorHash;
  }

  public void update(int rating, String message, Instant submittedAt) {
    this.rating = rating;
    this.message = message;
    this.createdAt = submittedAt;
  }

  public int rating() {
    return rating;
  }

  public String message() {
    return message;
  }

  public Instant createdAt() {
    return createdAt;
  }

  String visitorHash() {
    return visitorHash;
  }
}
