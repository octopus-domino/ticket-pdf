package ru.ticket.routing.trip;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.ticket.routing.trip.persistence.TripDraft;
import ru.ticket.routing.trip.persistence.TripDraftRepository;

@Service
public class TripDrafts {
  public record Trips(List<TripSummary> items, boolean hasNext) {}
  private static final List<String> ARRAYS = List.of(
      "travelers", "cities", "days", "legs", "accommodations", "documents");
  private final TripDraftRepository repository;
  private final ObjectMapper json;

  public TripDrafts(TripDraftRepository repository, ObjectMapper json) {
    this.repository = repository;
    this.json = json;
  }

  @Transactional(readOnly = true)
  public Trips list(String owner, int page) {
    if (page < 0 || page > 100_000) throw invalid("Invalid page");
    var slice = repository.listOwned(owner, PageRequest.of(page, 20));
    return new Trips(slice.getContent(), slice.hasNext());
  }

  @Transactional(readOnly = true)
  public Optional<JsonNode> find(String owner, String id) {
    return repository.findById(key(owner, id)).map(draft -> {
      try {
        return json.readTree(draft.payload());
      } catch (JsonProcessingException invalid) {
        throw new IllegalStateException("Invalid stored itinerary", invalid);
      }
    });
  }

  @Transactional
  public JsonNode save(String owner, String id, JsonNode trip) {
    var key = key(owner, id);
    if (trip == null || !trip.isObject() || !id.equals(trip.path("id").asText())
        || !trip.path("version").isIntegralNumber() || trip.path("version").asInt() != 2
        || !"TRIP".equals(trip.path("meta").path("mode").asText())
        || ARRAYS.stream().anyMatch(field -> !objectArray(trip.path(field)))) {
      throw invalid("journey.trip.invalid-data");
    }
    String title = trip.path("title").asText("").strip();
    if (!trip.path("title").isTextual() || title.isEmpty() || title.length() > 180) {
      throw invalid("journey.trip.invalid-data");
    }
    LocalDate start = date(trip, "startDate");
    LocalDate end = date(trip, "endDate");
    if (!end.isAfter(start)) throw invalid("journey.trip.minimum-two-days");
    var draft = repository.findById(key).orElseGet(() -> new TripDraft(key));
    draft.replace(trip.toString(), new TripSummary(id, title, start, end, Instant.now()));
    repository.saveAndFlush(draft);
    return trip;
  }

  private static LocalDate date(JsonNode trip, String field) {
    try {
      return LocalDate.parse(trip.path(field).asText());
    } catch (DateTimeParseException invalid) {
      throw invalid("journey.trip.minimum-two-days");
    }
  }

  private static boolean objectArray(JsonNode nodes) {
    if (!nodes.isArray()) return false;
    for (JsonNode node : nodes) {
      if (!node.isObject()) return false;
    }
    return true;
  }

  private static TripDraft.Key key(String owner, String id) {
    if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}")) throw invalid("journey.trip.invalid-id");
    return new TripDraft.Key(owner, id);
  }

  private static ResponseStatusException invalid(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
