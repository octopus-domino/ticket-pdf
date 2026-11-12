package ru.ticket.journey;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticket.common.ApiException;

/** Immutable, deduplicated itinerary snapshots keep previews and published PDFs reproducible. */
@Service
public class JourneyRouteService {
  public record Stop(
      String id, String label, double longitude, double latitude, String zone, int stayMinutes) {}

  public record Leg(
      String mode,
      double durationSeconds,
      String departure,
      String arrival,
      Integer beforeMinutes,
      Integer afterMinutes,
      List<List<Double>> coordinates,
      Double distanceMeters) {}

  public record Row(
      String kind, String label, String start, String end, String zone, String startZone) {
    public Row(String kind, String label, String start, String end, String zone) {
      this(kind, label, start, end, zone, null);
    }
  }

  public record Snapshot(
      List<Stop> stops, List<Leg> legs, String startLocal, List<Row> timeline, String mapId) {}

  public record Saved(String id) {}

  private final JourneyRouteRepository repository;
  private final JourneyMediaService media;
  private final ObjectMapper mapper;

  public JourneyRouteService(
      JourneyRouteRepository repository, JourneyMediaService media, ObjectMapper mapper) {
    this.repository = repository;
    this.media = media;
    this.mapper = mapper;
  }

  @Transactional
  public synchronized Saved save(Snapshot snapshot) {
    validate(snapshot);
    media.get(snapshot.mapId());
    try {
      String payload = mapper.writeValueAsString(snapshot);
      if (payload.length() > 750000) throw ApiException.badRequest("journey.route.invalid-route");
      String id =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(payload.getBytes(StandardCharsets.UTF_8)));
      if (!repository.existsById(id)) {
        if (repository.count() >= 256) throw ApiException.conflict("journey.route.storage-full");
        var route = new JourneyRoute();
        route.id = id;
        route.payload = payload;
        route.createdAt = Instant.now();
        repository.save(route);
      }
      return new Saved(id);
    } catch (java.io.IOException | java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  @Transactional(readOnly = true)
  public Snapshot get(String id) {
    if (id == null || !id.matches("[a-f0-9]{64}"))
      throw ApiException.badRequest("journey.route.invalid-route");
    var route = repository.findById(id).orElseThrow(() -> ApiException.notFound("Route"));
    try {
      return mapper.readValue(route.payload, Snapshot.class);
    } catch (java.io.IOException ex) {
      throw new IllegalStateException(ex);
    }
  }

  static void validate(Snapshot value) {
    if (value == null
        || value.stops() == null
        || value.stops().size() < 2
        || value.stops().size() > 12
        || value.legs() == null
        || value.legs().size() != value.stops().size() - 1
        || value.timeline() == null
        || value.timeline().isEmpty()
        || value.timeline().size() > 80
        || value.mapId() == null
        || !value.mapId().matches("[a-f0-9]{64}")) invalid();
    try {
      LocalDateTime.parse(value.startLocal());
      Set<String> ids = new HashSet<>();
      for (Stop stop : value.stops()) {
        if (stop == null
            || stop.id() == null
            || !stop.id().matches("[a-zA-Z0-9_-]{1,64}")
            || !ids.add(stop.id())
            || stop.label() == null
            || stop.label().isBlank()
            || stop.label().length() > 300
            || stop.stayMinutes() < 0
            || stop.stayMinutes() > 10080) invalid();
        BRouterRoadRouter.validate(new RoadRouter.Point(stop.longitude(), stop.latitude()));
        ZoneId.of(stop.zone());
      }
      int count = 0;
      for (Leg leg : value.legs()) {
        if (leg == null
            || !Set.of("car", "foot", "flight").contains(leg.mode())
            || !Double.isFinite(leg.durationSeconds())
            || leg.durationSeconds() < 0
            || leg.durationSeconds() > 604800
            || leg.coordinates() == null
            || leg.coordinates().size() < 2) invalid();
        if (leg.beforeMinutes() != null && (leg.beforeMinutes() < 0 || leg.beforeMinutes() > 10080))
          invalid();
        if (leg.afterMinutes() != null && (leg.afterMinutes() < 0 || leg.afterMinutes() > 10080))
          invalid();
        for (var coordinate : leg.coordinates()) {
          if (coordinate == null
              || coordinate.size() != 2
              || coordinate.get(0) == null
              || coordinate.get(1) == null) invalid();
          BRouterRoadRouter.validate(new RoadRouter.Point(coordinate.get(0), coordinate.get(1)));
          if (++count > 30000) invalid();
        }
      }
      Instant previous = null;
      for (Row row : value.timeline()) {
        if (row == null
            || !Set.of("stop", "car", "foot", "flight", "waiting", "checkin", "baggage")
                .contains(row.kind())
            || row.label() == null
            || row.label().length() > 650) invalid();
        Instant start = Instant.parse(row.start()), end = Instant.parse(row.end());
        ZoneId.of(row.zone());
        if (row.startZone() != null) ZoneId.of(row.startZone());
        if (end.isBefore(start)
            || (previous != null && !start.equals(previous))
            || Duration.between(start, end).toDays() > 7) invalid();
        previous = end;
      }
    } catch (DateTimeException | NullPointerException ex) {
      invalid();
    }
  }

  private static void invalid() {
    throw ApiException.badRequest("journey.route.invalid-route");
  }
}
