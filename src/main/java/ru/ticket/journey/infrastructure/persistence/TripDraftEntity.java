package ru.ticket.journey.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "journey_trip_drafts")
class TripDraftEntity {
  @Embeddable
  static class Key implements Serializable {
    @Column(name = "owner_id", length = 36)
    private String ownerId;

    @Column(name = "trip_id", length = 64)
    private String tripId;

    protected Key() {}

    Key(String ownerId, String tripId) {
      this.ownerId = ownerId;
      this.tripId = tripId;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Key key && Objects.equals(ownerId, key.ownerId)
          && Objects.equals(tripId, key.tripId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(ownerId, tripId);
    }
  }

  @EmbeddedId
  Key id;

  @Column(nullable = false, columnDefinition = "TEXT")
  String payload;

  @Version
  Long version;
}
