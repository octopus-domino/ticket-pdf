package ru.ticket.walks.infrastructure.poi;

import ru.ticket.walks.application.model.WalkPlace;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import ru.ticket.walks.shared.ApiException;
import ru.ticket.walks.application.map.AreaSearchCoordinator;
import ru.ticket.walks.domain.service.PoiSource;
import ru.ticket.walks.infrastructure.mcp.OsmMcpClient;

/** One area search combines category selectors and returns every classified result without maxStops. */
@Service
public class WalkAreaSearchService {
  public record Request(double longitude, double latitude, List<String> themes, String language,
                        double radiusKm, LocalDateTime visitTime) {}
  public record Result(List<WalkPlace> places, boolean complete) {}
  private static final Logger log = LoggerFactory.getLogger(WalkAreaSearchService.class);
  private static final Duration BUDGET = Duration.ofSeconds(20);
  private static final int DETAIL_BUDGET = 25;
  private static final Duration PRIMARY_WAIT = Duration.ofSeconds(8);
  private static final Set<String> LANGUAGES = Set.of("en", "ru", "de", "fr", "it", "es", "pl", "nl", "zh");
  private final WalkThemeProperties properties;
  private final PoiSource<OsmMcpClient.Poi, WalkThemeProperties.Theme> osm;
  private final TiledAreaOsmSearch tiles;
  private final WikimapiaPoiSource wikimapia;
  private final MysticThemeSource mystic;
  private final OpeningHoursEvaluator hours;
  private final ObjectMapper mapper;
  private final PoiSource<OsmMcpClient.Poi, WalkThemeProperties.Theme> fallback;
  private record SourceResult<T>(List<T> points, boolean available) {}
  private final AreaResultCache cache = new AreaResultCache();
  private final AreaSearchCoordinator searches = new AreaSearchCoordinator();
  private final ExecutorService sources = Executors.newFixedThreadPool(3, task -> {
    var thread = new Thread(task, "area-place-discovery"); thread.setDaemon(true); return thread;
  });

  public WalkAreaSearchService(WalkThemeProperties properties, OsmPoiSource osm, WikimapiaPoiSource wikimapia,
                              MysticThemeSource mystic, OpeningHoursEvaluator hours, ObjectMapper mapper) {
    this(properties, osm, wikimapia, mystic, hours, mapper, null);
  }

  public WalkAreaSearchService(WalkThemeProperties properties,
      @Qualifier("osmPoiSource") PoiSource<OsmMcpClient.Poi, WalkThemeProperties.Theme> osm,
      WikimapiaPoiSource wikimapia, MysticThemeSource mystic, OpeningHoursEvaluator hours, ObjectMapper mapper,
      PoiSource<OsmMcpClient.Poi, WalkThemeProperties.Theme> fallback) {
    this(properties, osm, wikimapia, mystic, hours, mapper, fallback, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public WalkAreaSearchService(WalkThemeProperties properties,
      @Qualifier("osmPoiSource") PoiSource<OsmMcpClient.Poi, WalkThemeProperties.Theme> osm,
      WikimapiaPoiSource wikimapia, MysticThemeSource mystic, OpeningHoursEvaluator hours, ObjectMapper mapper,
      @Qualifier("photonAreaPoiSource") PoiSource<OsmMcpClient.Poi, WalkThemeProperties.Theme> fallback,
      TiledAreaOsmSearch tiles) {
    this.tiles = tiles;
    this.properties = properties; this.osm = osm; this.wikimapia = wikimapia;
    this.mystic = mystic; this.hours = hours; this.mapper = mapper;
    this.fallback = fallback;
  }

  /** Validates before any provider work and keeps the response independent of selection order. */
  public List<WalkPlace> search(Request request) {
    return searchWithCoverage(request).places();
  }

  public Result searchWithCoverage(Request request) {
    validate(request);
    var selection = AreaThemeSelection.resolve(request.themes(), properties);
    var canonical = new Request(request.longitude(), request.latitude(), List.copyOf(selection.themes().keySet()),
        request.language(), request.radiusKm(), request.visitTime());
    var cached = cache.get(canonical);
    if (cached != null) return new Result(cached, true);
    return searches.execute(canonical, () -> searchUncached(canonical, selection));
  }

  private Result searchUncached(Request request, AreaThemeSelection selection) {
    var cached = cache.get(request);
    if (cached != null) return new Result(cached, true);
    var found = new LinkedHashMap<String, WalkPlace>();
    boolean complete = true;
    if (!selection.publicThemes().isEmpty()) {
      if (tiles == null) discover(request, selection, found);
      else complete = discoverTiled(request, selection, found);
    }
    if (selection.themes().containsKey("mystic")) addMystic(request, found);
    var result = found.values().stream().sorted(Comparator.comparingDouble(place ->
        WikimapiaPoiSourceImpl.distance(request.longitude(), request.latitude(), place.longitude(), place.latitude()))).toList();
    if (complete) cache.put(request, result);
    return new Result(result, complete);
  }

  private void validate(Request request) {
    if (!Double.isFinite(request.longitude()) || Math.abs(request.longitude()) >= 180
        || !Double.isFinite(request.latitude()) || Math.abs(request.latitude()) > 85
        || !Double.isFinite(request.radiusKm()) || request.radiusKm() < .1 || request.radiusKm() > 10
        || !LANGUAGES.contains(Objects.requireNonNullElse(request.language(), "")))
      throw ApiException.badRequest("journey.route.invalid-route");
  }

  private boolean discoverTiled(Request request, AreaThemeSelection selection,
                                Map<String, WalkPlace> found) {
    long deadline = System.nanoTime() + BUDGET.toNanos();
    var combined = selection.combined();
    var wikiTask = sources.submit(() -> {
      try (var budget = WalkBudget.until(deadline)) { return wikiBox(request, combined); }
    });
    Future<List<OsmMcpClient.Poi>> fallbackTask = null;
    try (var budget = WalkBudget.until(deadline)) {
      var coverage = tiles.search(request.longitude(), request.latitude(), (int) (request.radiusKm() * 1000),
          combined, request.language());
      coverage.points().forEach(point -> addOsm(request, selection, point, found));
      if (!coverage.available() && fallback != null) {
        fallbackTask = startFallback(request, combined, deadline);
        await(fallbackTask, deadline).points().forEach(point -> addOsm(request, selection, point, found));
      }
      var community = await(wikiTask, deadline);
      addWiki(request, selection, community.points(), deadline, found);
      return coverage.complete();
    } finally {
      wikiTask.cancel(true);
      if (fallbackTask != null) fallbackTask.cancel(true);
    }
  }

  private void discover(Request request, AreaThemeSelection selection, Map<String, WalkPlace> found) {
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
    Future<List<OsmMcpClient.Poi>> fallbackTask = null;
    try (var budget = WalkBudget.until(deadline)) {
      var points = await(osmTask, fallback == null ? deadline : Math.min(deadline, System.nanoTime() + PRIMARY_WAIT.toNanos()));
      if ((!points.available() || points.points().isEmpty()) && fallback != null) {
        fallbackTask = startFallback(request, combined, deadline);
        var alternate = await(fallbackTask, deadline);
        if (alternate.available() && !alternate.points().isEmpty()) {
          log.info("area.photon-fallback count={}", alternate.points().size());
          points = alternate;
        }
      }
      // A slow primary may have succeeded while the fallback was being awaited.
      if (!points.available()) points = await(osmTask, deadline);
      var community = await(wikiTask, deadline);
      if (!points.available() && community.points().isEmpty())
        throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "journey.route.places-unavailable");
      points.points().forEach(point -> addOsm(request, selection, point, found));
      addWiki(request, selection, community.points(), deadline, found);
    } finally {
      osmTask.cancel(true); wikiTask.cancel(true);
      if (fallbackTask != null) fallbackTask.cancel(true);
    }
  }

  private Future<List<OsmMcpClient.Poi>> startFallback(Request request, WalkThemeProperties.Theme combined, long deadline) {
    return sources.submit(() -> {
      try (var budget = WalkBudget.until(deadline)) {
        return fallback.searchArea(request.longitude(), request.latitude(), (int) (request.radiusKm() * 1000), combined, request.language());
      }
    });
  }

  private List<WikimapiaPoiSource.WikimapiaPoi> wikiBox(Request request, WalkThemeProperties.Theme combined) {
    if (!wikimapia.enabled()) return List.of();
    double dy = request.radiusKm() / 111.195, dx = dy / Math.cos(Math.toRadians(request.latitude()));
    return wikimapia.fetchInBox(request.longitude() - dx, request.latitude() - dy,
        request.longitude() + dx, request.latitude() + dy, combined, request.language());
  }

  private void addOsm(Request request, AreaThemeSelection selection, OsmMcpClient.Poi point,
                      Map<String, WalkPlace> found) {
    var themes = selection.matchingTags(point.tags());
    if (themes.isEmpty() || !nearby(request, point.lon(), point.lat())) return;
    String opening = point.tags().getOrDefault("opening_hours", "");
    var place = new WalkPlace(point.id().replace('/', '-'), point.label(), point.lon(), point.lat(),
        "https://www.openstreetmap.org/" + point.id(), null, opening, null,
        hours.evaluate(opening, request.visitTime()).name(), null, true, "osm", "", "", metadata(themes), null);
    merge(found, place);
  }

  private void addWiki(Request request, AreaThemeSelection selection, List<WikimapiaPoiSource.WikimapiaPoi> points,
                       long deadline, Map<String, WalkPlace> found) {
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
      merge(found, new WalkPlace("wikimapia-" + point.id(), point.name(), point.lon(), point.lat(),
          "https://wikimapia.org/" + point.id(), null, "", null, "UNKNOWN", null, null,
          "wikimapia", Long.toString(point.id()), description, metadata(matched), null));
    }
  }

  private void addMystic(Request request, Map<String, WalkPlace> found) {
    for (var point : mystic.findAllCandidates(request.longitude(), request.latitude(), request.radiusKm()))
      merge(found, WalkPlaceMapper.themedPlace(point, "mystic", mapper));
  }

  private boolean nearby(Request request, double lon, double lat) {
    return Double.isFinite(lon) && Double.isFinite(lat)
        && WikimapiaPoiSourceImpl.distance(request.longitude(), request.latitude(), lon, lat) <= request.radiusKm() * 1000;
  }

  private Map<String, String> metadata(List<String> themes) {
    return Map.of("themes", String.join(",", themes), "theme", themes.get(0));
  }

  /** The same physical place can belong to several selected themes without being duplicated. */
  private void merge(Map<String, WalkPlace> found, WalkPlace place) {
    String key = Math.round(place.longitude() * 100000) + ":" + Math.round(place.latitude() * 100000) + ":" + place.label().toLowerCase(Locale.ROOT);
    var previous = found.get(key);
    if (previous == null) { found.put(key, place); return; }
    var themes = new TreeSet<String>();
    themes.addAll(Arrays.asList(previous.meta().getOrDefault("themes", previous.meta().getOrDefault("theme", "")).split(",")));
    themes.addAll(Arrays.asList(place.meta().getOrDefault("themes", place.meta().getOrDefault("theme", "")).split(",")));
    var preferred = !place.description().isBlank() ? place : previous;
    found.put(key, new WalkPlace(preferred.id(), preferred.label(), preferred.longitude(), preferred.latitude(),
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
