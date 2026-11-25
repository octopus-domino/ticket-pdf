package ru.ticket.journey.application.journey;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.ticket.common.ApiException;
import ru.ticket.journey.JourneyBatch;
import ru.ticket.journey.JourneyBatchRepository;
import ru.ticket.journey.JourneyContentPolicy;
import ru.ticket.journey.JourneyService.Created;
import ru.ticket.journey.JourneyService.Input;
import ru.ticket.journey.JourneySiteService;
import ru.ticket.journey.ShortCodeGenerator;
import ru.ticket.journey.domain.model.JourneyEntity;
import ru.ticket.journey.domain.repository.JourneyStore;

import static ru.ticket.journey.JourneyService.MAX_RECIPIENTS;

/** Constructs and issues publications and their release registry entries. */
@Service
public class JourneyConstructorService {
  private static final SecureRandom RANDOM = new SecureRandom();
  private final JourneyStore journeys;
  private final JourneyBatchRepository batches;
  private final JourneySiteService sites;
  private final ShortCodeGenerator codes;
  private final JourneyContentPolicy policy;
  private final JourneyPublicationData publicationData;
  private final TransactionTemplate transactions;

  /** Wires the stores and the services used during issuance. */
  public JourneyConstructorService(JourneyStore journeys, JourneyBatchRepository batches, JourneySiteService sites,
      ShortCodeGenerator codes, JourneyContentPolicy policy, JourneyPublicationData publicationData,
      PlatformTransactionManager transactionManager) {
    this.journeys = journeys;
    this.batches = batches;
    this.sites = sites;
    this.codes = codes;
    this.policy = policy;
    this.publicationData = publicationData;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  /** Creates a release atomically, retrying short-code collisions. */
  public List<Created> create(Input input, String ownerId) {
    for (int attempt = 0; attempt < 8; attempt++) {
      try {
        return transactions.execute(status -> createBatch(input, ownerId));
      } catch (DataIntegrityViolationException collision) {
        if (attempt == 7) throw collision;
      }
    }
    throw new IllegalStateException("Could not allocate a short code");
  }

  private List<Created> createBatch(Input input, String ownerId) {
    JourneyPublicationData.validate(input);
    validateSharedFields(input);
    var recipients = recipients(input);
    var result = new ArrayList<Created>();
    String key = token();
    var batch = newBatch(input, ownerId, JourneyPublicationData.hash(key));
    for (String recipient : recipients) {
      var entity = issue(input, ownerId, recipient, result.size(), batch);
      result.add(new Created(publicationData.view(entity), key, batch.id));
    }
    batch.documentIds =
        result.stream()
            .map(value -> value.document().id())
            .collect(java.util.stream.Collectors.joining(","));
    batches.saveAndFlush(batch);
    return result;
  }

  private void validateSharedFields(Input input) {
    if (input.rows() == null || input.rows().isEmpty()) {
      policy.validate(input.kind(), input.data());
      publicationData.validatePoetry(input.kind(), input.data());
      if (input.data().containsKey("site"))
        sites.validateData(input.data().get("site"), input.kind(), input.data());
    }
  }

  private List<String> recipients(Input input) {
    List<String> recipients =
        input.recipients() == null || input.recipients().isEmpty()
            ? List.of("")
            : input.recipients();
    if (input.rows() != null && !input.rows().isEmpty()) {
      if (!input.kind().batch() || input.rows().size() > MAX_RECIPIENTS)
        throw ApiException.badRequest("Invalid batch rows");
      recipients =
          input.rows().stream()
              .map(row -> row == null ? "" : row.getOrDefault("recipient", ""))
              .toList();
    }
    if (recipients.size() > MAX_RECIPIENTS || recipients.size() > 1 && !input.kind().batch())
      throw ApiException.badRequest("Invalid recipient list");
    return recipients;
  }

  private JourneyBatch newBatch(Input input, String ownerId, String managementHash) {
    var batch = new JourneyBatch();
    batch.id = UUID.randomUUID().toString();
    batch.title = input.title().strip();
    batch.kind = input.kind().name();
    batch.ownerId = ownerId;
    batch.managementHash = managementHash;
    batch.createdAt = Instant.now();
    return batch;
  }

  private JourneyEntity issue(Input input, String ownerId, String recipient, int index, JourneyBatch batch) {
    if (recipient == null || recipient.length() > 100)
      throw ApiException.badRequest("Recipient name is too long");
    var entity = new JourneyEntity();
    entity.id = codes.next();
    entity.kind = input.kind().name();
    entity.title = input.title().strip();
    entity.ownerId = ownerId;
    entity.siteSlug = input.data().get("site");
    entity.managementHash = batch.managementHash;
    entity.createdAt = Instant.now();
    entity.updatedAt = entity.createdAt;
    var data = new LinkedHashMap<>(input.data());
    if (!recipient.isBlank()) data.put("recipient", recipient.strip());
    if (input.rows() != null && !input.rows().isEmpty()) {
      var row = input.rows().get(index);
      if (row == null || row.containsKey("site") || row.containsKey("theme"))
        throw ApiException.badRequest("Invalid batch row");
      data.putAll(row);
      JourneyPublicationData.validate(new Input(input.kind(), input.title(), data, List.of()));
      policy.validate(input.kind(), data);
      if (data.containsKey("site")) sites.validateData(data.get("site"), input.kind(), data);
    }
    entity.dataJson = publicationData.json(data);
    journeys.storeAndFlush(entity);
    return entity;
  }

  private static String token() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

}
