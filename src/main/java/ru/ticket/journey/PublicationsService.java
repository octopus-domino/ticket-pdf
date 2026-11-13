package ru.ticket.journey;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticket.common.ApiException;

/** Private, bounded account registry. Management secrets are never returned. */
@Service
public class PublicationsService {
  public record Results<T>(List<T> items, int page, int pages, long total) {}

  public record Document(
      JourneyService.View document, JourneyContentPolicy.Status status, String siteSlug) {}

  public record Site(JourneySiteService.Definition site, Instant updatedAt) {}

  public record Claim(String type, String id, String key) {}
  public record Release(String id, String title, String kind, int count, Instant createdAt) {}

  private final JourneyRepository documents;
  private final JourneySiteRepository sites;
  private final JourneyService journeys;
  private final ObjectMapper mapper;
  private JourneyBatchRepository batches;
  @org.springframework.beans.factory.annotation.Autowired
  void batches(JourneyBatchRepository batches) { this.batches = batches; }
  @Transactional(readOnly = true)
  public Results<Release> batches(String owner, int number) {
    if (number < 0 || number > 100000) throw ApiException.badRequest("Invalid page");
    var result = batches.findOwned(owner, PageRequest.of(number, 20, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    return new Results<>(result.getContent().stream().map(batch -> new Release(batch.id, batch.title, batch.kind,
        batch.documentIds.split(",").length, batch.createdAt)).toList(), number, result.getTotalPages(), result.getTotalElements());
  }

  public PublicationsService(
      JourneyRepository documents,
      JourneySiteRepository sites,
      JourneyService journeys,
      ObjectMapper mapper) {
    this.documents = documents;
    this.sites = sites;
    this.journeys = journeys;
    this.mapper = mapper;
  }

  private Pageable page(int page, String identity) {
    if (page < 0 || page > 100000) throw ApiException.badRequest("Invalid page");
    return PageRequest.of(
        page, 20, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc(identity)));
  }

  @Transactional(readOnly = true)
  public Results<Document> documents(String owner, int number, String site, String search) {
    if (site.length() > 48 || search.length() > 80) throw ApiException.badRequest("Invalid search");
    var result =
        documents.findOwned(
            owner, site, "%" + search.toLowerCase(Locale.ROOT) + "%", page(number, "id"));
    var items =
        result.getContent().stream()
            .map(
                entity -> {
                  var issued = journeys.describe(entity);
                  return new Document(issued.document(), issued.status(), entity.siteSlug);
                })
            .toList();
    return new Results<>(items, number, result.getTotalPages(), result.getTotalElements());
  }

  @Transactional(readOnly = true)
  public Results<Site> sites(String owner, int number) {
    var result = sites.findAllByOwnerId(owner, page(number, "slug"));
    var items =
        result.getContent().stream()
            .map(
                entity -> {
                  try {
                    return new Site(
                        mapper.readValue(entity.definition, JourneySiteService.Definition.class),
                        entity.updatedAt);
                  } catch (java.io.IOException error) {
                    throw new IllegalStateException(error);
                  }
                })
            .toList();
    return new Results<>(items, number, result.getTotalPages(), result.getTotalElements());
  }

  /** A legacy anonymous resource can be attached only with its existing management key. */
  @Transactional
  public Map<String, Integer> claim(String owner, List<Claim> claims) {
    if (claims == null || claims.size() > 100) throw ApiException.badRequest("Too many claims");
    int accepted = 0;
    for (var claim : claims) {
      if (claim == null || claim.id() == null || claim.id().length() > 48) continue;
      if ("site".equals(claim.type())) {
        var entity = sites.findById(claim.id()).orElse(null);
        if (entity != null
            && (entity.ownerId == null || owner.equals(entity.ownerId))
            && PublicationAccess.keyMatches(entity.managementHash, claim.key())) {
          entity.ownerId = owner;
          sites.save(entity);
          accepted++;
        }
      } else if ("document".equals(claim.type())) {
        var entity = documents.findById(claim.id()).orElse(null);
        if (entity != null
            && (entity.ownerId == null || owner.equals(entity.ownerId))
            && PublicationAccess.keyMatches(entity.managementHash, claim.key())) {
          entity.ownerId = owner;
          entity.siteSlug = journeys.describe(entity).document().data().get("site");
          documents.save(entity);
          accepted++;
        }
      }
    }
    return Map.of("accepted", accepted);
  }
}
