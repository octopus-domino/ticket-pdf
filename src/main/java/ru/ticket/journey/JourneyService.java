package ru.ticket.journey;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticket.common.ApiException;

@Service
public class JourneyService {
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
    PROPOSAL("proposals", false, false);

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

  private static final SecureRandom RANDOM = new SecureRandom();
  private final JourneyRepository journeys;
  private final JourneyResponseRepository responses;
  private final ObjectMapper mapper;
  private final JourneySiteService sites;
  private final ShortCodeGenerator codes;
  private final JourneyBatchRepository batches;
  private final JourneyContentPolicy policy;
  private final org.springframework.transaction.support.TransactionTemplate transactions;

  public JourneyService(
      JourneyRepository journeys,
      JourneyResponseRepository responses,
      ObjectMapper mapper,
      JourneySiteService sites,
      ShortCodeGenerator codes,
      JourneyBatchRepository batches,
      JourneyContentPolicy policy,
      org.springframework.transaction.PlatformTransactionManager transactionManager) {
    this.journeys = journeys;
    this.responses = responses;
    this.mapper = mapper;
    this.sites = sites;
    this.codes = codes;
    this.transactions =
        new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    this.batches = batches;
    this.policy = policy;
  }

  public List<Created> create(Input input) {
    for (int attempt = 0; attempt < 8; attempt++) {
      try {
        return transactions.execute(status -> createBatch(input));
      } catch (org.springframework.dao.DataIntegrityViolationException collision) {
        if (attempt == 7) throw collision;
      }
    }
    throw new IllegalStateException("Could not allocate a short code");
  }

  private List<Created> createBatch(Input input) {
    validate(input);
    if (input.rows() == null || input.rows().isEmpty()) {
      policy.validate(input.kind(), input.data());
      if (input.data().containsKey("site"))
        sites.validateData(input.data().get("site"), input.kind(), input.data());
    }
    List<String> recipients =
        input.recipients() == null || input.recipients().isEmpty()
            ? List.of("")
            : input.recipients();
    if (input.rows() != null && !input.rows().isEmpty()) {
      if (!input.kind().batch() || input.rows().size() > 100)
        throw ApiException.badRequest("Invalid batch rows");
      recipients =
          input.rows().stream()
              .map(row -> row == null ? "" : row.getOrDefault("recipient", ""))
              .toList();
    }
    if (recipients.size() > 100 || recipients.size() > 1 && !input.kind().batch())
      throw ApiException.badRequest("Invalid recipient list");
    var result = new ArrayList<Created>();
    String key = token();
    var batch = new JourneyBatch();
    batch.id = UUID.randomUUID().toString();
    batch.title = input.title().strip();
    batch.kind = input.kind().name();
    batch.managementHash = hash(key);
    batch.createdAt = Instant.now();
    for (String recipient : recipients) {
      if (recipient == null || recipient.length() > 100)
        throw ApiException.badRequest("Recipient name is too long");
      var entity = new JourneyEntity();
      entity.id = codes.next();
      entity.kind = input.kind().name();
      entity.title = input.title().strip();
      entity.managementHash = hash(key);
      entity.createdAt = Instant.now();
      entity.updatedAt = entity.createdAt;
      var data = new LinkedHashMap<>(input.data());
      if (!recipient.isBlank()) data.put("recipient", recipient.strip());
      if (input.rows() != null && !input.rows().isEmpty()) {
        var row = input.rows().get(result.size());
        if (row == null || row.containsKey("site") || row.containsKey("theme"))
          throw ApiException.badRequest("Invalid batch row");
        data.putAll(row);
        validate(new Input(input.kind(), input.title(), data, List.of()));
        policy.validate(input.kind(), data);
        if (data.containsKey("site")) sites.validateData(data.get("site"), input.kind(), data);
      }
      entity.dataJson = json(data);
      journeys.saveAndFlush(entity);
      result.add(new Created(view(entity), key, batch.id));
    }
    batch.documentIds =
        result.stream()
            .map(value -> value.document().id())
            .collect(java.util.stream.Collectors.joining(","));
    batches.saveAndFlush(batch);
    return result;
  }

  @Transactional(readOnly = true)
  public View get(String id) {
    return view(find(id));
  }

  @Transactional(readOnly = true)
  public View manage(String id, String key) {
    return view(authorize(id, key));
  }

  @Transactional
  public View update(String id, String key, Input input) {
    validate(input);
    var entity = authorize(id, key);
    policy.validate(input.kind(), input.data());
    if (input.data().containsKey("site"))
      sites.validateData(input.data().get("site"), input.kind(), input.data());
    if (!entity.kind.equals(input.kind().name()))
      throw ApiException.badRequest("Document type cannot change");
    entity.title = input.title().strip();
    entity.dataJson = json(input.data());
    entity.updatedAt = Instant.now();
    return view(journeys.save(entity));
  }

  @Transactional
  public void revoke(String id, String key) {
    var entity = authorize(id, key);
    entity.revoked = true;
    entity.updatedAt = Instant.now();
    journeys.save(entity);
    responses.deleteAllByJourneyId(id);
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
    String hash = hash(reply.responseKey());
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
    return responses.findAllByJourneyIdOrderByUpdatedAtDesc(id).stream()
        .map(reply -> new Guest(reply.name, reply.attending, reply.updatedAt))
        .toList();
  }

  private JourneyEntity authorize(String id, String key) {
    var entity = find(id);
    if (key == null
        || key.length() > 128
        || !MessageDigest.isEqual(
            entity.managementHash.getBytes(StandardCharsets.US_ASCII),
            hash(key).getBytes(StandardCharsets.US_ASCII)))
      throw ApiException.forbidden("The management link is invalid");
    return entity;
  }

  @Transactional(readOnly = true)
  public Batch batch(String id, String key) {
    var batch = batches.findById(id).orElseThrow(() -> ApiException.notFound("Release"));
    checkKey(batch.managementHash, key);
    var ids = List.of(batch.documentIds.split(","));
    var order = new HashMap<String, Integer>();
    for (int index = 0; index < ids.size(); index++) order.put(ids.get(index), index);
    var docs =
        journeys.findAllById(ids).stream()
            .sorted(java.util.Comparator.comparingInt(entity -> order.get(entity.id)))
            .map(
                entity -> {
                  var document = view(entity);
                  return new Issued(
                      document, policy.status(entity, document.data(), Instant.now()));
                })
            .toList();
    return new Batch(batch.id, batch.title, docs);
  }

  @Transactional(readOnly = true)
  public JourneyContentPolicy.Status status(String id) {
    var entity = journeys.findById(id).orElseThrow(() -> ApiException.notFound("Document"));
    return policy.status(entity, view(entity).data(), Instant.now());
  }

  @Transactional
  public JourneyContentPolicy.Status consume(String id, String key) {
    var entity = journeys.lockById(id).orElseThrow(() -> ApiException.notFound("Document"));
    checkKey(entity.managementHash, key);
    var status = policy.status(entity, view(entity).data(), Instant.now());
    if (!status.consumable()) throw ApiException.badRequest("This document cannot be redeemed");
    if (!status.state().equals("ACTIVE"))
      throw ApiException.conflict("Код уже использован, отозван или просрочен.");
    entity.usedAt = Instant.now();
    entity.updatedAt = entity.usedAt;
    journeys.save(entity);
    return policy.status(entity, view(entity).data(), Instant.now());
  }

  private static void checkKey(String expected, String key) {
    if (key == null
        || key.length() > 128
        || !MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.US_ASCII),
            hash(key).getBytes(StandardCharsets.US_ASCII)))
      throw ApiException.forbidden("The management link is invalid");
  }

  private JourneyEntity find(String id) {
    var entity = journeys.findById(id).orElseThrow(() -> ApiException.notFound("Document"));
    if (entity.revoked)
      throw new ApiException(
          org.springframework.http.HttpStatus.GONE, "This document has been withdrawn");
    return entity;
  }

  private View view(JourneyEntity entity) {
    try {
      return new View(
          entity.id,
          Kind.valueOf(entity.kind),
          entity.title,
          mapper.readValue(entity.dataJson, new TypeReference<Map<String, String>>() {}),
          entity.updatedAt);
    } catch (java.io.IOException error) {
      throw new IllegalStateException("Stored journey is invalid", error);
    }
  }

  private String json(Map<String, String> data) {
    try {
      return mapper.writeValueAsString(data);
    } catch (java.io.IOException error) {
      throw new IllegalArgumentException("Invalid document data", error);
    }
  }

  static void validate(Input input) {
    if (input == null
        || input.kind() == null
        || input.title() == null
        || input.title().isBlank()
        || input.title().length() > 120
        || input.data() == null
        || input.data().size() > 20)
      throw ApiException.badRequest("Please complete the document title");
    for (var field : input.data().entrySet()) {
      int limit =
          (input.kind() == Kind.PROPOSAL && field.getKey().equals("positions"))
                  || (input.kind() == Kind.SCHEDULE && field.getKey().equals("events"))
              ? 80000
              : 12000;
      if (!field.getKey().matches("[a-z][a-z0-9_]{0,31}")
          || Set.of("__proto__", "constructor", "prototype").contains(field.getKey())
          || field.getValue() == null
          || field.getValue().length() > limit)
        throw ApiException.badRequest("Invalid or oversized document field");
    }
    if (input.data().values().stream().mapToInt(String::length).sum()
        > (Set.of(Kind.PROPOSAL, Kind.SCHEDULE).contains(input.kind()) ? 100000 : 32000))
      throw ApiException.badRequest("Document fields are too large");
    if (input.kind() == Kind.PROPOSAL) {
      ProposalContent.read(input.data().getOrDefault("positions", "[]"));
      for (String key : List.of("issuer", "recipient"))
        if (input.data().getOrDefault(key, "").length() > 100)
          throw ApiException.badRequest("Company and recipient: maximum 100 characters");
      if (!input.data().getOrDefault("currency", "RUB").matches("[A-Z]{3}"))
        throw ApiException.badRequest("Use a three-letter currency code, e.g. RUB");
    }
    if (input.data().getOrDefault("items", "").lines().count() > 150)
      throw ApiException.badRequest("A menu can contain up to 150 lines");
  }

  private static String token() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String hash(String key) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
