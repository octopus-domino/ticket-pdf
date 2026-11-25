package ru.ticket.journey;

import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface JourneyReviewRepository extends JpaRepository<JourneyReview, String> {
  Optional<JourneyReview> findByJourneyIdAndVisitorHash(String journeyId, String visitorHash);

  Page<JourneyReview> findByJourneyId(String id, Pageable pageable);

  long countByJourneyId(String id);

  @Query("select coalesce(avg(r.rating),0.0) from JourneyReview r where r.journeyId=:id")
  double average(@Param("id") String id);
}
