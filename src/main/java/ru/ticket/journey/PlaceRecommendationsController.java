package ru.ticket.journey;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.ticket.common.ApiException;
import java.time.LocalDateTime;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import ru.ticket.journey.walk.*;

@RestController
@RequestMapping("/api/journeys/routes/places")
public class PlaceRecommendationsController {
  private static final Logger log = LoggerFactory.getLogger(PlaceRecommendationsController.class);

  private final String key;
  private final ObjectMapper mapper;
  private final OsmPlaceService osm;
  private long nextRequest;
  private WikimapiaPlaces wikimapia;
  private WalkThemeService walks;
  private PhotoEnricher photos;
  private WalkThemeProperties themes;

  private final Semaphore searchPermit = new Semaphore(1);
  private final Semaphore enrichPermit = new Semaphore(1);

  @Value("${app.journeys.wikimapia.bbox-radius-km:1.5}")
  private double walkRadius = 1.5;

  @org.springframework.beans.factory.annotation.Autowired
  void walks(WalkThemeService walks, PhotoEnricher photos, WalkThemeProperties themes) {
    this.walks = walks;
    this.photos = photos;
    this.themes = themes;
  }

  @org.springframework.beans.factory.annotation.Autowired
  void wikimapia(WikimapiaPlaces source) { this.wikimapia = source; }

  public PlaceRecommendationsController(
          ObjectMapper mapper,
          @Value("${app.journeys.twogis-key:${TWOGIS_API_KEY:}}") String key,
          OsmPlaceService osm) {
    this.mapper = mapper;
    this.key = key;
    this.osm = osm;
  }

  public record Place(
          String id, String label, double longitude, double latitude, String url, JsonNode schedule,
          String openingHours, String photoReference, String visitStatus, Integer wikidataSitelinks,
          Boolean inOsm, String source, String wikimapiaId, String description,
          Map<String, String> meta, Double score) {
    public Place(String id, String label, double longitude, double latitude, String url,
                 JsonNode schedule, String openingHours, String photoReference) {
      this(id, label, longitude, latitude, url, schedule, openingHours, photoReference,
              null, null, null, null, null, null, null, null);
    }
    public Place(String id, String label, double longitude, double latitude, String url,
                 JsonNode schedule, String openingHours) {
      this(id, label, longitude, latitude, url, schedule, openingHours, null);
    }
  }

  public record Result(boolean available, List<Place> places, String source) {}

  @GetMapping("/wikimapia-{id}")
  public WikimapiaPlaces.Details details(
          @PathVariable String id, @RequestParam(defaultValue = "en") String language) {
    return withSearchPermit(() -> {
      if (wikimapia == null || !wikimapia.enabled()) throw ApiException.notFound("Place source");
      try { return wikimapia.details(id, language); }
      catch (java.io.IOException error) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "journey.route.places-unavailable");
      }
    }, 0L);
  }

  @GetMapping("/themed")
  public Result themedSearch(
          @RequestParam double longitude, @RequestParam double latitude, @RequestParam String theme,
          @RequestParam(required = false) LocalDateTime visitTime, @RequestParam(defaultValue = "en") String language,
          @RequestParam(defaultValue = "7") int maxStops, @RequestParam(required = false) Integer maxSitelinks) {
    return withSearchPermit(() -> {
      long started = System.nanoTime();
      var candidates = walks.findCandidates(
              new WalkThemeService.WalkRequest(longitude, latitude, theme, visitTime, language, maxStops, walkRadius, maxSitelinks));
      var places = candidates.stream().map(c -> themedPlace(c, theme, mapper)).toList();
      log.info("walk.themed theme={} lang={} count={} elapsed_ms={}", theme, language, places.size(), (System.nanoTime() - started) / 1_000_000);
      return new Result(true, places, "OpenStreetMap / Wikimapia");
    }, 0L);
  }

  @GetMapping("/themes")
  public Set<String> themes() { return themes.getWalkThemes().keySet(); }

  @PostMapping("/themed/enrich")
  public List<JourneyRouteService.Photo> photosForStops(
          @RequestBody List<JourneyRouteService.Stop> stops, @RequestParam(defaultValue = "en") String language) {
    return withEnrichPermit(() -> {
      long started = System.nanoTime();
      var result = photos.enrich(stops, language);
      log.info("walk.enrich stops={} photos={} elapsed_ms={}", stops.size(), result.size(), (System.nanoTime() - started) / 1_000_000);
      return result;
    });
  }

  public static Place themedPlace(
          WalkThemeService.WalkCandidate point, String theme, ObjectMapper mapper) {
    var meta = new LinkedHashMap<String, String>();
    meta.put("source", point.source());
    meta.put("theme", theme);
    meta.put("in_osm", Boolean.toString(point.inOsm()));
    meta.put("visit_status", point.visitStatus());
    if (!point.openingHours().isBlank()) meta.put("opening_hours", point.openingHours());
    if (!point.wikidataId().isBlank()) meta.put("wikidata", point.wikidataId());
    if (point.wikidataSitelinks() != null) meta.put("wikidata_sitelinks", point.wikidataSitelinks().toString());
    if (!point.wikimapiaId().isBlank()) meta.put("wikimapia_id", point.wikimapiaId());
    if (!point.description().isBlank())
      meta.put("wikimapia_description", point.description().substring(0, Math.min(500, point.description().length())));
    String url = point.source().equals("wikimapia")
            ? "https://wikimapia.org/" + point.wikimapiaId()
            : "https://www.openstreetmap.org/" + point.id().replaceFirst("-", "/");
    return new Place(point.id(), point.label(), point.lon(), point.lat(), url,
            OsmOpeningHours.parse(point.openingHours(), mapper), point.openingHours(), null,
            point.visitStatus(), point.wikidataSitelinks(), point.inOsm(), point.source(),
            point.wikimapiaId(), point.description(), Map.copyOf(meta), point.score());
  }

  private <T> T withSearchPermit(Supplier<T> action, long throttleMs) {
    if (!searchPermit.tryAcquire()) {
      log.warn("walk.busy reason=search-permit uri=/places");
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "journey.route.search-busy");
    }
    try {
      if (throttleMs > 0) {
        synchronized (this) {
          long now = System.currentTimeMillis();
          if (now < nextRequest) {
            log.warn("walk.busy reason=throttle uri=/places");
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "journey.route.search-busy");
          }
          nextRequest = now + throttleMs;
        }
      }
      return action.get();
    } finally {
      searchPermit.release();
    }
  }

  private <T> T withEnrichPermit(Supplier<T> action) {
    if (!enrichPermit.tryAcquire()) {
      log.warn("walk.busy reason=enrich-permit uri=/themed/enrich");
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "journey.route.search-busy");
    }
    try { return action.get(); }
    finally { enrichPermit.release(); }
  }

  @GetMapping
  public Result search(
          @RequestParam double longitude, @RequestParam double latitude,
          @RequestParam(defaultValue = "attractions") String kind, @RequestParam(defaultValue = "en") String language) {
    return withSearchPermit(() -> searchProviders(longitude, latitude, kind, language), 0L);
  }

  private synchronized Result searchProviders(double longitude, double latitude, String kind, String language) {
    BRouterRoadRouter.validate(new RoadRouter.Point(longitude, latitude));
    if (!Set.of("attractions", "hotels").contains(kind)) throw ApiException.badRequest("journey.route.invalid-route");
    if (kind.equals("attractions") && wikimapia != null && wikimapia.enabled()) {
      try {
        var places = wikimapia.search(longitude, latitude, language);
        if (!places.isEmpty()) return new Result(true, places, "Wikimapia");
      } catch (java.io.IOException unavailable) { }
    }
    if (key.isBlank())
      return new Result(true, osm.search(longitude, latitude, kind, language), "OpenStreetMap / Wikimedia");
    if (System.currentTimeMillis() < nextRequest)
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "journey.route.search-busy");
    nextRequest = System.currentTimeMillis() + 1100;
    String query = kind.equals("hotels") ? "гостиницы" : "достопримечательности";
    HttpURLConnection connection = null;
    try {
      URI uri = URI.create("https://catalog.api.2gis.com/3.0/items?type=branch&page_size=10&radius=15000&point="
              + longitude + "," + latitude + "&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
              + "&fields=items.point,items.schedule&key=" + URLEncoder.encode(key, StandardCharsets.UTF_8));
      connection = (HttpURLConnection) uri.toURL().openConnection();
      connection.setConnectTimeout(4000);
      connection.setReadTimeout(8000);
      connection.setInstanceFollowRedirects(false);
      if (connection.getResponseCode() != 200) throw new java.io.IOException();
      byte[] bytes;
      try (var input = connection.getInputStream()) { bytes = input.readNBytes(262145); }
      if (bytes.length > 262144) throw new java.io.IOException();
      var payload = mapper.readTree(bytes);
      if (payload.path("meta").path("code").asInt() != 200) throw new java.io.IOException();
      var places = new ArrayList<Place>();
      for (var item : payload.path("result").path("items")) {
        if (places.size() >= 10) break;
        String id = item.path("id").asText(""), name = item.path("name").asText("");
        double lon = item.path("point").path("lon").asDouble(Double.NaN),
                lat = item.path("point").path("lat").asDouble(Double.NaN);
        if (!id.matches("[0-9_]{1,80}") || name.isBlank() || name.length() > 250
                || !Double.isFinite(lon) || !Double.isFinite(lat)
                || Math.abs(lon) >= 180 || Math.abs(lat) > 85) continue;
        places.add(new Place(id, name, lon, lat, "https://2gis.ru/firm/" + id, item.path("schedule"), ""));
      }
      return new Result(true, List.copyOf(places), "2GIS");
    } catch (java.io.IOException ex) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "journey.route.unavailable");
    } finally { if (connection != null) connection.disconnect(); }
  }
}
