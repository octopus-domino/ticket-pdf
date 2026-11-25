package ru.ticket.journey.domain.repository;

import java.util.Optional;
import ru.ticket.journey.domain.model.Trip;

/** Stores private v2 itineraries under an owner-scoped identifier. */
public interface TripRepository {
  /** Finds a saved itinerary belonging to the specified owner. */
  Optional<Trip> findOwned(String ownerId, String tripId);

  /** Persists an explicitly saved itinerary for its owner. */
  Trip saveOwned(String ownerId, Trip trip);
}
