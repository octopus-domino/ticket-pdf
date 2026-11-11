package ru.ticket.journey;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JourneyRepository extends JpaRepository<JourneyEntity, String> {
  @org.springframework.data.jpa.repository.Query(
      """
      select j from JourneyEntity j
      where (j.ownerId = :owner or exists (
        select s.slug from JourneySite s where s.slug = j.siteSlug and s.ownerId = :owner))
      and (:site = '' or j.siteSlug = :site)
      and lower(j.title) like :search
      """)
  org.springframework.data.domain.Page<JourneyEntity> findOwned(
      @org.springframework.data.repository.query.Param("owner") String owner,
      @org.springframework.data.repository.query.Param("site") String site,
      @org.springframework.data.repository.query.Param("search") String search,
      org.springframework.data.domain.Pageable pageable);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query(
      "select journey from JourneyEntity journey where journey.id = :id")
  java.util.Optional<JourneyEntity> lockById(
      @org.springframework.data.repository.query.Param("id") String id);
}
