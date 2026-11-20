package ru.ticket.journey.infrastructure.persistence;

import ru.ticket.journey.domain.model.JourneyRoute;
import ru.ticket.journey.domain.repository.RouteSnapshotStore;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data adapter for the existing route snapshot table. */
public interface JourneyRouteRepository extends JpaRepository<JourneyRoute, String>, RouteSnapshotStore {
  @Override
  default void store(JourneyRoute snapshot) {
    save(snapshot);
  }
}
