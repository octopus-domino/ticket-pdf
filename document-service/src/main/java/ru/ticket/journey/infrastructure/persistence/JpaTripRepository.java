package ru.ticket.journey.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.ticket.journey.domain.model.Trip;
import ru.ticket.journey.domain.repository.TripRepository;

/** JSON snapshot adapter for explicitly saved private trips, enabled with the trip planner. */
@Repository
@ConditionalOnProperty(name = "app.journeys.trip-planner-enabled", havingValue = "true")
public class JpaTripRepository implements TripRepository {
  private final EntityManager entities;
  private final ObjectMapper mapper;

  public JpaTripRepository(EntityManager entities, ObjectMapper mapper) {
    this.entities = entities;
    this.mapper = mapper;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Trip> findOwned(String ownerId, String tripId) {
    return Optional.ofNullable(entities.find(TripDraftEntity.class, key(ownerId, tripId)))
        .map(row -> decode(row.payload));
  }

  @Override
  @Transactional
  public Trip saveOwned(String ownerId, Trip trip) {
    var key = key(ownerId, trip.id());
    String payload = encode(trip);
    var row = entities.find(TripDraftEntity.class, key);
    if (row == null) {
      row = new TripDraftEntity();
      row.id = key;
      row.payload = payload;
      entities.persist(row);
    } else {
      row.payload = payload;
    }
    return trip;
  }

  private static TripDraftEntity.Key key(String owner, String tripId) {
    if (owner == null || owner.isBlank() || owner.length() > 36 ||
        tripId == null || tripId.isBlank() || tripId.length() > 64) {
      throw new IllegalArgumentException("journey.trip.invalid-id");
    }
    return new TripDraftEntity.Key(owner, tripId);
  }

  private String encode(Trip trip) {
    try {
      return mapper.writeValueAsString(trip);
    } catch (JsonProcessingException invalid) {
      throw new IllegalArgumentException("journey.trip.invalid-data", invalid);
    }
  }

  private Trip decode(String payload) {
    try {
      return mapper.readValue(payload, Trip.class);
    } catch (JsonProcessingException invalid) {
      throw new IllegalStateException("journey.trip.invalid-data", invalid);
    }
  }
}
