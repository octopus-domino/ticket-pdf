package ru.ticket.journey;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JourneyRepository extends JpaRepository<JourneyEntity, String> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query(
      "select journey from JourneyEntity journey where journey.id = :id")
  java.util.Optional<JourneyEntity> lockById(
      @org.springframework.data.repository.query.Param("id") String id);
}
