package ru.ticket.journey;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JourneyResponseRepository extends JpaRepository<JourneyResponse, String> {
  List<JourneyResponse> findAllByJourneyIdOrderByUpdatedAtDesc(String journeyId);

  Optional<JourneyResponse> findByJourneyIdAndResponseHash(String journeyId, String responseHash);

  void deleteAllByJourneyId(String journeyId);
}
