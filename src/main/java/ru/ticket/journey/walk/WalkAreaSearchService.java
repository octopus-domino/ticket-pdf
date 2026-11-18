package ru.ticket.journey.walk;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.ticket.common.ApiException;
import ru.ticket.journey.*;
import ru.ticket.mcp.OsmMcpClient;

/** One area search combines category selectors and returns every classified result without maxStops. */
@Service
public class WalkAreaSearchService {
  public record Request(double longitude, double latitude, List<String> themes, String language,
                        double radiusKm, LocalDateTime visitTime) {}
  private static final Logger log = LoggerFactory.getLogger(WalkAreaSearchService.class);
  private static final Duration BUDGET = Duration.ofSeconds(20);
  private static final int DETAIL_BUDGET = 25;
  private static final Set<String> LANGUAGES = Set.of("en", "ru", "de", "fr", "it", "es", "pl", "nl", "zh");
  private final WalkThemeProperties properties;
  private final OsmPoiSource osm;
  private final WikimapiaPoiSource wikimapia;
  private final MysticThemeSource mystic;
  private final OpeningHoursEvaluator hours;
  private final ObjectMapper mapper;
  private record SourceResult<T>(List<T> points, boolean available) {}
  private final WalkCache<String, List<PlaceRecommendationsController.Place>> cache = new WalkCache<>(64, Duration.ofMinutes(10));
  private final ExecutorService sources = Executors.newFixedThreadPool(2, task -> {
    var thread = new Thread(task, "area-place-discovery"); thread.setDaemon(true); return thread;
  });

  public WalkAreaSearchService(WalkThemeProperties properties, OsmPoiSource osm, WikimapiaPoiSource wikimapia,
                              MysticThemeSource mystic, OpeningHoursEvaluator hours, ObjectMapper mapper) {
    this.properties = properties; this.osm = osm; this.wikimapia = wikimapia;
    this.mystic = mystic; this.hours = hours; this.mapper = mapper;
  }

  /** Validates before any provider work and keeps the response independent of selection order. */
  public List<PlaceRecommendationsController.Place> search(Request request) {
    validate(request);
    var selection = AreaThemeSelection.resolve(request.themes(), properties);
    String key = selection.themes().keySet() + ":" + request.longitude() + ":" + request.latitude()
        + ":" + request.radiusKm() + ":" + request.language() + ":" + request.visitTime();
    var cached = cache.get(key);
    if (cached != null) return cached;
    var found = new LinkedHashMap<String, PlaceRecommendationsController.Place>();
    if (!selection.publicThemes().isEmpty()) discover(request, selection, found);
    if (selection.themes().containsKey("mystic")) addMystic(request, found);
    var result = found.values().stream().sorted(Comparator.comparingDouble(place ->
        WikimapiaPoiSourceImpl.distance(request.longitude(), request.latitude(), place.longitude(), place.latitude()))).toList();
    if (!result.isEmpty()) cache.put(key, result);
    return result;
  }

  private void validate(Request request) {
    if (!Double.isFinite(request.longitude()) || Math.abs(request.longitude()) >= 180
        || !Double.isFinite(request.latitude()) || Math.abs(request.latitude()) > 85
        || !Double.isFinite(request.radiusKm()) || request.radiusKm() < .1 || request.radiusKm() > 10
        || !LANGUAGES.contains(Objects.requireNonNullElse(request.language(), "")))
      throw ApiException.badRequest("journey.route.invalid-route");
  }

  private void discover(Request request, AreaThemeSelection selection, Map<String, PlaceRecommendationsController.Place> found) {
    long deadline = System.nanoTime() + BUDGET.toNanos();
    var combined = selection.combined();
    var osmTask = sources.submit(() -> {
      try (var budget = WalkBudget.until(deadline)) {
        return osm.searchArea(request.longitude(), request.latitude(), (int) (request.radiusKm() * 1000), combined, request.language());
      }
    });
    var wikiTask = sources.submit(() -> {
      try (var budget = WalkBudget.until(deadline)) { return wikiBox(request, combined); }
    });
    try (var budget = WalkBudget.until(deadline)) {
      var points = await(osmTask, deadline);
      var community = await(wikiTask, deadline);
      if (!points.available() && community.points().isEmpty())
        throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "journey.route.places-unavailable");
      points.points().forEach(point -> addOsm(request, selection, point, found));
      addWiki(request, selection, community.points(), deadline, found);
    } finally {
      osmTask.cancel(true); wikiTask.cancel(true);
    }
  }

  private List<WikimapiaPoiSource.WikimapiaPoi> wikiBox(Request request, WalkThemeProperties.Theme combined) {
    if (!wikimapia.enabled()) return List.of();
    double dy = request.radiusKm() / 111.195, dx = dy / Math.cos(Math.toRadians(request.latitude()));
    return wikimapia.fetchInBox(request.longitude() - dx, request.latitude() - dy,
        request.longitude() + dx, request.latitude() + dy, combined, request.language());
  }

  private void addOsm(Request request, AreaThemeSelection selection, OsmMcpClient.Poi point,
                      Map<String, PlaceRecommendationsController.Place> found) {
    var themes = selection.matchingTags(point.tags());
    if (themes.isEmpty() || !nearby(request, point.lon(), point.lat())) return;
    String opening = point.tags().getOrDefault("opening_hours", "");
    var place = new PlaceRecommendationsController.Place(point.id().replace('/', '-'), point.label(), point.lon(), point.lat(),
        "https://www.openstreetmap.org/" + point.id(), null, opening, null,
        hours.evaluate(opening, request.visitTime()).name(), null, true, "osm", "", "", metadata(themes), null);
    merge(found, place);
  }

  private void addWiki(Request request, AreaThemeSelection selection, List<WikimapiaPoiSource.WikimapiaPoi> points,
                       long deadline, Map<String, PlaceRecommendationsController.Place> found) {
    int details = 0;
    for (var point : points) {
      if (!nearby(request, point.lon(), point.lat())) continue;
      var matched = selection.matchingCategories(point.categories(), "");
      String description = "";
      if (System.nanoTime() < deadline && details++ < DETAIL_BUDGET) {
        var detail = wikimapia.enrich(point.id(), request.language());
        if (detail == null) continue;
        description = detail.description(); matched = selection.matchingCategories(detail.categoryNames(), description);
      }
      if (matched.isEmpty()) continue;
      merge(found, new PlaceRecommendationsController.Place("wikimapia-" + point.id(), point.name(), point.lon(), point.lat(),
          "https://wikimapia.org/" + point.id(), null, "", null, "UNKNOWN", null, null,
          "wikimapia", Long.toString(point.id()), description, metadata(matched), null));
    }
  }

  private void addMystic(Request request, Map<String, PlaceRecommendationsController.Place> found) {
    for (var point : mystic.findAllCandidates(request.longitude(), request.latitude(), request.radiusKm()))
      merge(found, PlaceRecommendationsController.themedPlace(point, "mystic", mapper));
  }

  private boolean nearby(Request request, double lon, double lat) {
    return Double.isFinite(lon) && Double.isFinite(lat)
        && WikimapiaPoiSourceImpl.distance(request.longitude(), request.latitude(), lon, lat) <= request.radiusKm() * 1000;
  }

  private Map<String, String> metadata(List<String> themes) {
    return Map.of("themes", String.join(",", themes), "theme", themes.get(0));
  }

  /** The same physical place can belong to several selected themes without being duplicated. */
  private void merge(Map<String, PlaceRecommendationsController.Place> found, PlaceRecommendationsController.Place place) {
    String key = Math.round(place.longitude() * 100000) + ":" + Math.round(place.latitude() * 100000) + ":" + place.label().toLowerCase(Locale.ROOT);
    var previous = found.get(key);
    if (previous == null) { found.put(key, place); return; }
    var themes = new TreeSet<String>();
    themes.addAll(Arrays.asList(previous.meta().getOrDefault("themes", previous.meta().getOrDefault("theme", "")).split(",")));
    themes.addAll(Arrays.asList(place.meta().getOrDefault("themes", place.meta().getOrDefault("theme", "")).split(",")));
    var preferred = !place.description().isBlank() ? place : previous;
    found.put(key, new PlaceRecommendationsController.Place(preferred.id(), preferred.label(), preferred.longitude(), preferred.latitude(),
        preferred.url(), preferred.schedule(), preferred.openingHours(), preferred.photoReference(), preferred.visitStatus(), preferred.wikidataSitelinks(),
        preferred.inOsm(), preferred.source(), preferred.wikimapiaId(), preferred.description(), metadata(List.copyOf(themes)), preferred.score()));
  }

  private <T> SourceResult<T> await(Future<List<T>> task, long deadline) {
    try { return new SourceResult<>(task.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS), true); }
    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return new SourceResult<>(List.of(), false); }
    catch (ExecutionException | TimeoutException unavailable) {
      log.warn("area.place-source unavailable type={}", unavailable.getClass().getSimpleName());
      return new SourceResult<>(List.of(), false);
    }
  }

  @PreDestroy void close() { sources.shutdownNow(); }
}
