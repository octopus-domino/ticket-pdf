package ru.ticket.journey;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JourneySiteRepository extends JpaRepository<JourneySite, String> {
  boolean existsBySlugAndOwnerId(String slug, String ownerId);

  org.springframework.data.domain.Page<JourneySite> findAllByOwnerId(
      String ownerId, org.springframework.data.domain.Pageable pageable);
}
