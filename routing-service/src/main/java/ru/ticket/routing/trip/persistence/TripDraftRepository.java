package ru.ticket.routing.trip.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.ticket.routing.trip.TripSummary;

public interface TripDraftRepository extends JpaRepository<TripDraft, TripDraft.Key> {
  @Query("""
      select new ru.ticket.routing.trip.TripSummary(t.id.tripId, t.title, t.startDate, t.endDate, t.updatedAt)
      from TripDraft t where t.id.ownerId = :owner
      order by t.updatedAt desc nulls last, t.id.tripId desc
      """)
  Slice<TripSummary> listOwned(@Param("owner") String owner, Pageable pageable);
}
