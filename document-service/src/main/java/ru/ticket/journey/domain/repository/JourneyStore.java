package ru.ticket.journey.domain.repository;

import java.util.List;
import java.util.Optional;
import ru.ticket.journey.domain.model.JourneyEntity;

/** Persistence operations required by publication workflows. */
public interface JourneyStore {
  /** Results of an ownership-filtered and stably ordered publication search. */
  record OwnedPage(List<JourneyEntity> items, int pages, long total) {}

  /** Loads an existing publication. */
  Optional<JourneyEntity> findById(String id);

  /** Loads the existing publications referenced by the requested identifiers. */
  List<JourneyEntity> findAllById(Iterable<String> ids);

  /** Loads and locks a publication for the duration of the current transaction. */
  Optional<JourneyEntity> lockById(String id);

  /** Stores a publication. */
  JourneyEntity store(JourneyEntity publication);

  /** Stores a publication and flushes pending writes in the current transaction. */
  JourneyEntity storeAndFlush(JourneyEntity publication);

  /** Searches visible publications in descending update order with a stable ID tie-break. */
  OwnedPage findOwned(String owner, String site, String searchPattern, String kind, int page, int size);
}
