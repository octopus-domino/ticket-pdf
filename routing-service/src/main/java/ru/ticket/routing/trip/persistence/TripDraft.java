package ru.ticket.routing.trip.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import ru.ticket.routing.trip.TripSummary;

@Entity
@Table(name = "journey_trip_drafts")
public class TripDraft {
  @Embeddable
  public record Key(@Column(name = "owner_id", length = 36) String ownerId,
                    @Column(name = "trip_id", length = 64) String tripId) implements Serializable {}

  @EmbeddedId
  private Key id;
  @Column(nullable = false, columnDefinition = "TEXT")
  private String payload;
  @Version
  private Long version;
  @Column(length = 180)
  private String title;
  @Column(name = "start_date")
  private LocalDate startDate;
  @Column(name = "end_date")
  private LocalDate endDate;
  @Column(name = "updated_at")
  private Instant updatedAt;

  protected TripDraft() {}

  public TripDraft(Key id) {
    this.id = id;
  }

  public String payload() {
    return payload;
  }

  public void replace(String payload, TripSummary summary) {
    this.payload = payload;
    title = summary.title();
    startDate = summary.startDate();
    endDate = summary.endDate();
    updatedAt = summary.updatedAt();
  }
}
