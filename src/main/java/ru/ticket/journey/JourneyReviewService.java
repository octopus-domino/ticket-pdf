package ru.ticket.journey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticket.common.ApiException;

@Service
public class JourneyReviewService {
  public record Input(int rating, String message, String responseKey) {}

  public record Review(int rating, String message, Instant createdAt) {}

  public record Results(List<Review> items, int page, int pages, long total, double average) {}

  private final JourneyReviewRepository reviews;
  private final JourneyRepository journeys;
  private final JourneyService documents;
  private final PublicationAccess access;

  public JourneyReviewService(
      JourneyReviewRepository reviews,
      JourneyRepository journeys,
      JourneyService documents,
      PublicationAccess access) {
    this.reviews = reviews;
    this.journeys = journeys;
    this.documents = documents;
    this.access = access;
  }

  @Transactional
  public void submit(String id, Input input) {
    if (input == null
        || input.rating() < 1
        || input.rating() > 5
        || input.message() == null
        || input.message().length() > 2000
        || input.responseKey() == null
        || !input.responseKey().matches("[A-Za-z0-9_-]{32,128}"))
      throw ApiException.badRequest("journey.review.invalid");
    var document = journeys.lockById(id).orElseThrow(() -> ApiException.notFound("Document"));
    if (!document.kind.equals("PRICETAG")
        || document.revoked
        || !documents.status(id).state().equals("ACTIVE"))
      throw ApiException.badRequest("journey.review.closed");
    String hash;
    try {
      hash =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(input.responseKey().getBytes(StandardCharsets.US_ASCII)));
    } catch (java.security.NoSuchAlgorithmException error) {
      throw new IllegalStateException(error);
    }
    var review =
        reviews
            .findByJourneyIdAndVisitorHash(id, hash)
            .orElseGet(
                () -> {
                  if (reviews.countByJourneyId(id) >= 1000)
                    throw ApiException.conflict("journey.review.closed");
                  var created = new JourneyReview();
                  created.id = UUID.randomUUID().toString();
                  created.journeyId = id;
                  created.visitorHash = hash;
                  return created;
                });
    review.rating = input.rating();
    review.message = input.message().strip();
    review.createdAt = Instant.now();
    reviews.save(review);
  }

  @Transactional(readOnly = true)
  public Results list(String id, String key, String owner, int page) {
    if (key != null && !key.isBlank()) documents.manage(id, key);
    else if (owner != null) access.document(id, owner);
    else throw ApiException.forbidden("Invalid management key");
    if (page < 0 || page > 10000) throw ApiException.badRequest("Invalid page");
    var result =
        reviews.findByJourneyId(
            id, PageRequest.of(page, 20, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    return new Results(
        result.getContent().stream()
            .map(r -> new Review(r.rating, r.message, r.createdAt))
            .toList(),
        page,
        result.getTotalPages(),
        result.getTotalElements(),
        reviews.average(id));
  }
}
