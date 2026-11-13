package ru.ticket.journey;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.ticket.common.ApiException;

/** Small, cached, explicitly requested Overpass searches; never scans an entire city/country. */
@Service
public class OsmPlaceService {
  private record Cached(long expires, List<PlaceRecommendationsController.Place> places) {}
  private final Map<String, Cached> cache = new LinkedHashMap<>(64, .75f, true);
  private final Semaphore permit = new Semaphore(1);
  private final ObjectMapper mapper;
  private final String endpoint;
  private long nextRequest;
  public OsmPlaceService(ObjectMapper mapper,
      @Value("${app.journeys.overpass-url:https://overpass-api.de/api/interpreter}") String endpoint) {
    this.mapper = mapper; this.endpoint = endpoint;
  }
  public List<PlaceRecommendationsController.Place> search(double longitude, double latitude, String kind, String language) {
    BRouterRoadRouter.validate(new RoadRouter.Point(longitude, latitude));
    if (!Set.of("attractions", "hotels").contains(kind)) throw ApiException.badRequest("journey.route.invalid-route");
    if (!permit.tryAcquire()) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "journey.route.search-busy");
    try {
      String locale = Set.of("ru", "en", "de", "fr", "it", "es", "pl", "zh", "nl").contains(language) ? language : "en";
      String key = String.format(Locale.ROOT, "%.3f:%.3f:%s:%s", longitude, latitude, kind, locale);
      var found = cache.get(key);
      if (found != null && found.expires > System.currentTimeMillis()) return found.places;
      if (System.currentTimeMillis() < nextRequest) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "journey.route.search-busy");
      nextRequest = System.currentTimeMillis() + 2000;
      String categories = kind.equals("hotels") ? "hotel|guest_house|hostel" : "attraction|museum|gallery|viewpoint|zoo";
      String query = "[out:json][timeout:12][maxsize:16777216];nwr[\"tourism\"~\"^(" + categories
          + ")$\"](around:6000," + latitude + "," + longitude + ");out tags center 60;";
      HttpURLConnection connection = null;
      try {
        connection = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
        connection.setConnectTimeout(4000); connection.setReadTimeout(20000); connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("POST"); connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        connection.setRequestProperty("User-Agent", "CoDoc/1.0 (https://codoc.site; nearby trip planning)");
        byte[] form = ("data=" + URLEncoder.encode(query, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(form.length);
        try (var output = connection.getOutputStream()) { output.write(form); }
        if (connection.getResponseCode() != 200) throw new java.io.IOException();
        byte[] body;
        try (var input = connection.getInputStream()) { body = input.readNBytes(1_000_001); }
        if (body.length > 1_000_000) throw new java.io.IOException();
        var payload = mapper.readTree(body);
        if (!payload.path("elements").isArray() || payload.has("remark")) throw new java.io.IOException();
        var places = readPlaces(payload, longitude, latitude, locale, mapper);
        cache.put(key, new Cached(System.currentTimeMillis() + 600000, places));
        while (cache.size() > 64) cache.remove(cache.keySet().iterator().next());
        return places;
      } catch (java.io.IOException ex) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "journey.route.places-unavailable");
      } finally { if (connection != null) connection.disconnect(); }
    } finally { permit.release(); }
  }
  static List<PlaceRecommendationsController.Place> readPlaces(JsonNode payload, double longitude, double latitude, String language, ObjectMapper mapper) {
    var places = new ArrayList<PlaceRecommendationsController.Place>();
    var seen = new HashSet<String>();
    for (var element : payload.path("elements")) {
      var tags = element.path("tags");
      String name = tags.path("name:" + language).asText(tags.path("name").asText(""));
      String type = element.path("type").asText(""), number = element.path("id").asText("");
      var position = element.has("center") ? element.path("center") : element;
      double lon = position.path("lon").asDouble(Double.NaN), lat = position.path("lat").asDouble(Double.NaN);
      if (!Set.of("node", "way", "relation").contains(type) || !number.matches("[0-9]{1,20}") || name.isBlank() || name.length() > 250
          || !Double.isFinite(lon) || !Double.isFinite(lat) || Math.abs(lon) >= 180 || Math.abs(lat) > 85
          || Math.hypot((lon - longitude) * Math.cos(latitude * Math.PI / 180), lat - latitude) > .07) continue;
      String duplicate = name.toLowerCase(Locale.ROOT) + ":" + Math.round(lon * 1000) + ":" + Math.round(lat * 1000);
      if (!seen.add(duplicate)) continue;
      String hours = tags.path("opening_hours").asText("");
      if (hours.length() > 300) hours = "";
      places.add(new PlaceRecommendationsController.Place(type + "-" + number, name, lon, lat,
          "https://www.openstreetmap.org/" + type + "/" + number, OsmOpeningHours.parse(hours, mapper), hours,
          photoReference(tags)));
    }
    places.sort(Comparator.comparingDouble(place -> Math.hypot((place.longitude() - longitude) * Math.cos(latitude * Math.PI / 180), place.latitude() - latitude)));
    return places.stream().limit(10).toList();
  }
  private static String photoReference(JsonNode tags) {
    String commons = tags.path("wikimedia_commons").asText("").strip();
    if (commons.startsWith("File:") && commons.length() <= 240
        && !commons.matches("(?s).*[\\p{Cntrl}|#].*")) return commons;
    String wikidata = tags.path("wikidata").asText("").strip();
    return wikidata.matches("Q[1-9][0-9]{0,15}") ? wikidata : null;
  }
}
