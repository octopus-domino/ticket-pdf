package ru.ticket.journey;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JourneyBatchRepository extends JpaRepository<JourneyBatch, String> {
  @org.springframework.data.jpa.repository.Query("""
      select b from JourneyBatch b where b.ownerId = :owner or exists (
        select j.id from JourneyEntity j where
        locate(concat(',', j.id, ','), concat(',', b.documentIds, ',')) > 0
        and (j.ownerId = :owner or exists (select s.slug from JourneySite s where s.slug = j.siteSlug and s.ownerId = :owner)))
      """)
  org.springframework.data.domain.Page<JourneyBatch> findOwned(
      @org.springframework.data.repository.query.Param("owner") String owner, org.springframework.data.domain.Pageable page);
}
