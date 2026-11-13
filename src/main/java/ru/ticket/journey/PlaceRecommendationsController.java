package ru.ticket.journey;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.ticket.common.ApiException;

/** Optional 2GIS discovery. Credentials remain on the server; no hotel rates are fabricated. */
@RestController
@RequestMapping("/api/journeys/routes/places")
public class PlaceRecommendationsController {
  private final String key;
  private final ObjectMapper mapper;
  private final OsmPlaceService osm;
  private long nextRequest;
  public PlaceRecommendationsController(ObjectMapper mapper, @Value("${app.journeys.twogis-key:${TWOGIS_API_KEY:}}") String key, OsmPlaceService osm) {
    this.mapper = mapper; this.key = key; this.osm = osm;
  }
  public record Place(String id, String label, double longitude, double latitude, String url, JsonNode schedule, String openingHours, String photoReference) {
    public Place(String id, String label, double longitude, double latitude, String url, JsonNode schedule, String openingHours) {
      this(id, label, longitude, latitude, url, schedule, openingHours, null);
    }
  }
  public record Result(boolean available, List<Place> places, String source) {}

  @GetMapping
  public synchronized Result search(@RequestParam double longitude, @RequestParam double latitude,
      @RequestParam(defaultValue = "attractions") String kind, @RequestParam(defaultValue = "en") String language) {
    BRouterRoadRouter.validate(new RoadRouter.Point(longitude, latitude));
    if (!Set.of("attractions", "hotels").contains(kind)) throw ApiException.badRequest("journey.route.invalid-route");
    if (key.isBlank()) return new Result(true, osm.search(longitude, latitude, kind, language), "OpenStreetMap");
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
      connection.setConnectTimeout(4000); connection.setReadTimeout(8000); connection.setInstanceFollowRedirects(false);
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
        double lon = item.path("point").path("lon").asDouble(Double.NaN), lat = item.path("point").path("lat").asDouble(Double.NaN);
        if (!id.matches("[0-9_]{1,80}") || name.isBlank() || name.length() > 250 || !Double.isFinite(lon) || !Double.isFinite(lat)
            || Math.abs(lon) >= 180 || Math.abs(lat) > 85) continue;
        places.add(new Place(id, name, lon, lat, "https://2gis.ru/firm/" + id, item.path("schedule"), ""));
      }
      return new Result(true, List.copyOf(places), "2GIS");
    } catch (java.io.IOException ex) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "journey.route.unavailable");
    } finally { if (connection != null) connection.disconnect(); }
  }
}
