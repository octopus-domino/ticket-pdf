package ru.ticket.journey;

import ru.ticket.journey.domain.repository.JourneyStore;
import ru.ticket.journey.domain.model.JourneyEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticket.common.ApiException;
import ru.ticket.journey.application.journey.JourneyConstructorService;
import ru.ticket.journey.application.journey.JourneyPublicationData;

@Service
public class JourneyService {
  public static final int MAX_RECIPIENTS = 200;
  public enum Kind {
    INVITATION("invite", false, false),
    CERTIFICATE("certificates", true, false),
    MENU("menu", false, false),
    TICKET("tickets", true, true),
    GIFT("gifts", true, true),
    BADGE("badges", true, false),
    PASS("passes", true, false),
    RESUME("resume", false, false),
    SCHEDULE("schedule", false, false),
    GREETING("greetings", true, false),
    ALBUM("albums", false, false),
    POETRY("poetry", false, false),
    PROPOSAL("proposals", false, false),
    VCARD("cards", true, false),
    LABEL("labels", true, false),
    PRICETAG("price-tags", true, false),
    PROPERTY("properties", false, false),
    TOUR("tours", true, false);

    private final String slug;
    private final boolean batch;
    private final boolean consumable;

    Kind(String slug, boolean batch, boolean consumable) {
      this.slug = slug;
      this.batch = batch;
      this.consumable = consumable;
    }

    public String slug() {
      return slug;
    }

    public boolean batch() {
      return batch;
    }

    public boolean consumable() {
      return consumable;
    }
  }

  public record Input(
      Kind kind,
      String title,
      Map<String, String> data,
      List<String> recipients,
      List<Map<String, String>> rows) {
    public Input(Kind kind, String title, Map<String, String> data, List<String> recipients) {
      this(kind, title, data, recipients, null);
    }
  }

  public record View(
      String id, Kind kind, String title, Map<String, String> data, Instant updatedAt) {}

  public record Created(View document, String managementKey, String batchId) {}

  public record Issued(View document, JourneyContentPolicy.Status status) {}

  public record Batch(String id, String title, List<Issued> documents) {}

  public record Reply(String name, boolean attending, String responseKey) {}

  public record Guest(String name, boolean attending, Instant updatedAt) {}

  private final JourneyStore journeys;
  private final JourneyResponseRepository responses;
  private final JourneySiteService sites;
  private final JourneyBatchRepository batches;
  private final JourneyContentPolicy policy;
  private final PublicationAccess access;
  private final JourneyConstructorService constructor;
  private final JourneyPublicationData publicationData;

  public JourneyService(JourneyStore journeys, JourneyResponseRepository responses, JourneySiteService sites,
      JourneyBatchRepository batches, JourneyContentPolicy policy, PublicationAccess access,
      JourneyConstructorService constructor, JourneyPublicationData publicationData) {
    this.journeys = journeys;
    this.responses = responses;
    this.sites = sites;
    this.batches = batches;
    this.policy = policy;
    this.access = access;
    this.constructor = constructor;
    this.publicationData = publicationData;
  }

  public List<Created> create(Input input) {
    return create(input, null);
  }

  public List<Created> create(Input input, String ownerId) {
    return constructor.create(input, ownerId);
  }

  @Transactional(readOnly = true)
  public View get(String id) {
    return publicationData.view(find(id));
  }

  @Transactional(readOnly = true)
  public View manage(String id, String key) {
    return publicationData.view(authorize(id, key));
  }

  @Transactional(readOnly = true)
  public View manageOwned(String id, String owner) {
    return publicationData.view(access.document(id, owner));
  }
  void lockForAlbum(String id) {
    journeys.lockById(id).orElseThrow(() -> ApiException.notFound("Album"));
  }

  @Transactional
  public View update(String id, String key, Input input) {
    return updateData(authorize(id, key), input);
  }

  @Transactional
  public View updateOwned(String id, String owner, Input input) {
    return updateData(access.document(id, owner), input);
  }

  private View updateData(JourneyEntity entity, Input input) {
    JourneyPublicationData.validate(input);
    if (!Objects.equals(publicationData.view(entity).data().get("site"), input.data().get("site")))
      throw ApiException.badRequest("The published site cannot change");
    policy.validate(input.kind(), input.data());
    publicationData.validatePoetry(input.kind(), input.data());
    if (input.data().containsKey("site"))
      sites.validateData(input.data().get("site"), input.kind(), input.data());
    if (!entity.kind.equals(input.kind().name()))
      throw ApiException.badRequest("Document type cannot change");
    entity.title = input.title().strip();
    entity.dataJson = publicationData.json(input.data());
    entity.updatedAt = Instant.now();
    return publicationData.view(journeys.store(entity));
  }

  @Transactional
  public void revoke(String id, String key) {
    revokeEntity(authorize(id, key));
  }

  @Transactional
  public void revokeOwned(String id, String owner) {
    revokeEntity(access.document(id, owner));
  }

  private void revokeEntity(JourneyEntity entity) {
    entity.revoked = true;
    entity.updatedAt = Instant.now();
    journeys.store(entity);
    responses.deleteAllByJourneyId(entity.id);
  }

  @Transactional
  public void respond(String id, Reply reply) {
    var entity = journeys.lockById(id).orElseThrow(() -> ApiException.notFound("Document"));
    if (entity.revoked)
      throw new ApiException(
          org.springframework.http.HttpStatus.GONE, "This document has been withdrawn");
    if (!entity.kind.equals("INVITATION"))
      throw ApiException.badRequest("RSVP is available for invitations only");
    if (reply == null
        || reply.name() == null
        || reply.name().isBlank()
        || reply.name().length() > 100
        || reply.responseKey() == null
        || reply.responseKey().length() < 32
        || reply.responseKey().length() > 128)
      throw ApiException.badRequest("Enter your name and try again");
    String hash = JourneyPublicationData.hash(reply.responseKey());
    var response =
        responses.findByJourneyIdAndResponseHash(id, hash).orElseGet(JourneyResponse::new);
    if (response.id == null) response.id = UUID.randomUUID().toString();
    response.journeyId = id;
    response.responseHash = hash;
    response.name = reply.name().strip();
    response.attending = reply.attending();
    response.updatedAt = Instant.now();
    responses.save(response);
  }

  @Transactional(readOnly = true)
  public List<Guest> guests(String id, String key) {
    authorize(id, key);
    return guestList(id);
  }

  @Transactional(readOnly = true)
  public List<Guest> guestsOwned(String id, String owner) {
    access.document(id, owner);
    return guestList(id);
  }

  private List<Guest> guestList(String id) {
    return responses.findAllByJourneyIdOrderByUpdatedAtDesc(id).stream()
        .map(reply -> new Guest(reply.name, reply.attending, reply.updatedAt))
        .toList();
  }

  private JourneyEntity authorize(String id, String key) {
    // Revocation hides the public document, while its owner must retain management access.
    var entity = journeys.findById(id).orElseThrow(() -> ApiException.notFound("Document"));
    if (key == null
        || key.length() > 128
        || !MessageDigest.isEqual(
            entity.managementHash.getBytes(StandardCharsets.US_ASCII),
            JourneyPublicationData.hash(key).getBytes(StandardCharsets.US_ASCII)))
      throw ApiException.forbidden("The management link is invalid");
    return entity;
  }

  @Transactional(readOnly = true)
  public Batch batch(String id, String key) {
    var batch = batches.findById(id).orElseThrow(() -> ApiException.notFound("Release"));
    checkKey(batch.managementHash, key);
    return describeBatch(batch);
  }

  @Transactional(readOnly = true)
  public Batch batchOwned(String id, String owner) {
    var batch = batches.findById(id).orElseThrow(() -> ApiException.notFound("Release"));
    if (owner == null) throw ApiException.notFound("Release");
    // Legacy releases have no owner column. Check every member rather than granting an entire release from one claimed document.
    if (!owner.equals(batch.ownerId)) for (String document : batch.documentIds.split(",")) access.document(document, owner);
    return describeBatch(batch);
  }

  private Batch describeBatch(JourneyBatch batch) {
    var ids = List.of(batch.documentIds.split(","));
    var order = new HashMap<String, Integer>();
    for (int index = 0; index < ids.size(); index++) order.put(ids.get(index), index);
    var docs =
        journeys.findAllById(ids).stream()
            .sorted(java.util.Comparator.comparingInt(entity -> order.get(entity.id)))
            .map(
                entity -> {
                  var document = publicationData.view(entity);
                  return new Issued(
                      document, policy.status(entity, document.data(), Instant.now()));
                })
            .toList();
    return new Batch(batch.id, batch.title, docs);
  }

  public record StatusChange(String action) {}
  @Transactional
  public JourneyContentPolicy.Status changeStatus(String id, String key, String owner, StatusChange change) {
    var entity = journeys.lockById(id).orElseThrow(() -> ApiException.notFound("Document"));
    if (key != null && !key.isBlank()) checkKey(entity.managementHash, key); else access.document(id, owner);
    if (change == null || change.action() == null) throw ApiException.badRequest("Invalid status action");
    switch (change.action()) {
      case "REVOKE" -> { entity.revoked = true; responses.deleteAllByJourneyId(id); }
      case "RESTORE" -> entity.revoked = false;
      case "RESET_USAGE" -> {
        if (!policy.status(entity, publicationData.view(entity).data(), Instant.now()).consumable()) throw ApiException.badRequest("This document cannot be redeemed");
        entity.usedAt = null;
      }
      default -> throw ApiException.badRequest("Invalid status action");
    }
    entity.updatedAt = Instant.now(); journeys.store(entity);
    return policy.status(entity, publicationData.view(entity).data(), Instant.now());
  }

  @Transactional(readOnly = true)
  public JourneyContentPolicy.Status status(String id) {
    var entity = journeys.findById(id).orElseThrow(() -> ApiException.notFound("Document"));
    return policy.status(entity, publicationData.view(entity).data(), Instant.now());
  }

  @Transactional
  public JourneyContentPolicy.Status consume(String id, String key) {
    var entity = journeys.lockById(id).orElseThrow(() -> ApiException.notFound("Document"));
    checkKey(entity.managementHash, key);
    return consumeEntity(entity);
  }

  @Transactional
  public JourneyContentPolicy.Status consumeOwned(String id, String owner) {
    access.document(id, owner);
    var entity = journeys.lockById(id).orElseThrow(() -> ApiException.notFound("Document"));
    return consumeEntity(entity);
  }

  private JourneyContentPolicy.Status consumeEntity(JourneyEntity entity) {
    var status = policy.status(entity, publicationData.view(entity).data(), Instant.now());
    if (!status.consumable()) throw ApiException.badRequest("This document cannot be redeemed");
    if (!status.state().equals("ACTIVE"))
      throw ApiException.conflict("Код уже использован, отозван или просрочен.");
    entity.usedAt = Instant.now();
    entity.updatedAt = entity.usedAt;
    journeys.store(entity);
    return policy.status(entity, publicationData.view(entity).data(), Instant.now());
  }

  private static void checkKey(String expected, String key) {
    if (key == null
        || key.length() > 128
        || !MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.US_ASCII),
            JourneyPublicationData.hash(key).getBytes(StandardCharsets.US_ASCII)))
      throw ApiException.forbidden("The management link is invalid");
  }

  private JourneyEntity find(String id) {
    var entity = journeys.findById(id).orElseThrow(() -> ApiException.notFound("Document"));
    if (entity.revoked)
      throw new ApiException(
          org.springframework.http.HttpStatus.GONE, "This document has been withdrawn");
    return entity;
  }

  Issued describe(JourneyEntity entity) {
    var document = publicationData.view(entity);
    return new Issued(document, policy.status(entity, document.data(), Instant.now()));
  }

}
