package ru.ticket.journey.domain.repository;

import java.util.Optional;
import ru.ticket.journey.domain.model.JourneyRoute;

/** Storage for immutable route snapshots addressed by their content hash. */
public interface RouteSnapshotStore {
  /** Tests whether the snapshot already exists. */
  boolean existsById(String id);

  /** Counts stored snapshots for the shared storage limit. */
  long count();

  /** Stores the snapshot. */
  void store(JourneyRoute snapshot);

  /** Reads the snapshot identified by its hash. */
  Optional<JourneyRoute> findById(String id);
}
