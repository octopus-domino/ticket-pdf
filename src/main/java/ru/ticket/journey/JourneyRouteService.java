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
      String id, String label, double longitude, double latitude, String zone, int stayMinutes, String description, String walkId) {
    public Stop(String id, String label, double longitude, double latitude, String zone, int stayMinutes, String description) {
      this(id, label, longitude, latitude, zone, stayMinutes, description, null);
    }
    public Stop(String id, String label, double longitude, double latitude, String zone, int stayMinutes) {
      this(id, label, longitude, latitude, zone, stayMinutes, null);
    }
  }

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
      String kind, String label, String start, String end, String zone, String startZone, String description) {
    public Row(String kind, String label, String start, String end, String zone, String startZone) {
      this(kind, label, start, end, zone, startZone, null);
    }
    public Row(String kind, String label, String start, String end, String zone) {
      this(kind, label, start, end, zone, null);
    }
  }

  public record Photo(String stopId, String imageId, String sourceUrl, String author, String license, String licenseUrl) {}
  public record Snapshot(
      List<Stop> stops, List<Leg> legs, String startLocal, List<Row> timeline, String mapId, List<Photo> photos) {
    public Snapshot(List<Stop> stops, List<Leg> legs, String startLocal, List<Row> timeline, String mapId) {
      this(stops, legs, startLocal, timeline, mapId, null);
    }
  }

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
    if (snapshot.photos() != null) for (var photo : snapshot.photos()) media.get(photo.imageId());
    for (var stop : snapshot.stops()) if (stop.walkId() != null) {
      var walk = get(stop.walkId());
      if (walk.stops().stream().anyMatch(item -> item.walkId() != null)
          || walk.legs().stream().anyMatch(leg -> !"foot".equals(leg.mode()))) invalid();
    }
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
            || (stop.description() != null && stop.description().length() > 500)
            || (stop.walkId() != null && !stop.walkId().matches("[a-f0-9]{64}"))
            || stop.stayMinutes() < 0
            || stop.stayMinutes() > 10080) invalid();
        BRouterRoadRouter.validate(new RoadRouter.Point(stop.longitude(), stop.latitude()));
        ZoneId.of(stop.zone());
      }
      int count = 0;
      if (value.photos() != null) {
        if (value.photos().size() > 4) invalid();
        Set<String> photographed = new HashSet<>();
        for (var photo : value.photos()) {
          if (photo == null || !ids.contains(photo.stopId()) || !photographed.add(photo.stopId())
              || photo.imageId() == null || !photo.imageId().matches("[a-f0-9]{64}")
              || photo.sourceUrl() == null || !photo.sourceUrl().matches("https://commons\\.wikimedia\\.org/wiki/File:[^\\p{Cntrl}<>\" ]{1,1500}")
              || photo.author() == null || photo.author().isBlank() || photo.author().length() > 500
              || photo.license() == null || photo.license().isBlank() || photo.license().length() > 80
              || photo.licenseUrl() == null || !photo.licenseUrl().matches("https://creativecommons\\.org/(licenses|publicdomain)/[a-z0-9./-]{1,120}")) invalid();
        }
      }
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
            || row.label().length() > 650
            || (row.description() != null && row.description().length() > 500)) invalid();
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
