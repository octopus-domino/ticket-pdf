package ru.ticket.journey.infrastructure.persistence;

import ru.ticket.journey.domain.model.JourneyEntity;
import ru.ticket.journey.domain.repository.JourneyStore;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data adapter for publication lookup, locking and ownership queries. */
public interface JourneyRepository extends JpaRepository<JourneyEntity, String>, JourneyStore {
  @Override
  default JourneyEntity store(JourneyEntity publication) {
    return save(publication);
  }

  @Override
  default JourneyEntity storeAndFlush(JourneyEntity publication) {
    return saveAndFlush(publication);
  }

  @Override
  default OwnedPage findOwned(String owner, String site, String searchPattern, String kind, int page, int size) {
    var result = findOwned(owner, site, searchPattern, kind,
        PageRequest.of(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));
    return new OwnedPage(result.getContent(), result.getTotalPages(), result.getTotalElements());
  }

  @org.springframework.data.jpa.repository.Query(
      """
      select j from JourneyEntity j
      where (j.ownerId = :owner or exists (
        select s.slug from JourneySite s where s.slug = j.siteSlug and s.ownerId = :owner))
      and (:site = '' or j.siteSlug = :site)
      and (:kind = '' or j.kind = :kind)
      and lower(j.title) like :search
      """)
  org.springframework.data.domain.Page<JourneyEntity> findOwned(
      @org.springframework.data.repository.query.Param("owner") String owner,
      @org.springframework.data.repository.query.Param("site") String site,
      @org.springframework.data.repository.query.Param("search") String search,
      @org.springframework.data.repository.query.Param("kind") String kind,
      org.springframework.data.domain.Pageable pageable);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query(
      "select journey from JourneyEntity journey where journey.id = :id")
  java.util.Optional<JourneyEntity> lockById(
      @org.springframework.data.repository.query.Param("id") String id);
}
