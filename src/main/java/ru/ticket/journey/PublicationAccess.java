package ru.ticket.journey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
import ru.ticket.common.ApiException;

/** Account ownership is separate from public management-link authorization. */
@Component
public class PublicationAccess {
  private final JourneyRepository documents;
  private final JourneySiteRepository sites;

  public PublicationAccess(JourneyRepository documents, JourneySiteRepository sites) {
    this.documents = documents;
    this.sites = sites;
  }

  public JourneyEntity document(String id, String owner) {
    var entity = documents.findById(id).orElseThrow(() -> ApiException.notFound("Publication"));
    if (owner == null
        || !(owner.equals(entity.ownerId)
            || entity.siteSlug != null && sites.existsBySlugAndOwnerId(entity.siteSlug, owner)))
      throw ApiException.notFound("Publication");
    return entity;
  }

  public JourneySite site(String slug, String owner) {
    var entity = sites.findById(slug).orElseThrow(() -> ApiException.notFound("Site"));
    if (owner == null || !owner.equals(entity.ownerId)) throw ApiException.notFound("Site");
    return entity;
  }

  static boolean keyMatches(String hash, String key) {
    if (key == null || key.length() < 32 || key.length() > 128) return false;
    try {
      var actual =
          java.util.HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(key.getBytes(StandardCharsets.UTF_8)));
      return MessageDigest.isEqual(
          hash.getBytes(StandardCharsets.US_ASCII), actual.getBytes(StandardCharsets.US_ASCII));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
