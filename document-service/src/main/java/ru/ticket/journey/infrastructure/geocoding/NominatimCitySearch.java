package ru.ticket.journey.infrastructure.geocoding;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ru.ticket.common.ApiException;
import ru.ticket.journey.domain.service.CitySearch;

/** Explicit city search against a deployment-configured Nominatim instance. */
@Component
@ConditionalOnProperty(name = "app.journeys.trip-planner-enabled", havingValue = "true")
public class NominatimCitySearch implements CitySearch {
  private static final Set<String> SETTLEMENTS = Set.of("city", "town", "village", "municipality", "hamlet");
  private final ObjectMapper mapper;
  private final String endpoint;
  private final LinkedHashMap<String, Cached> cache = new LinkedHashMap<>(64, .75f, true);
  private Instant nextRequest = Instant.EPOCH;
  private record Cached(Instant expires, List<City> cities) {}

  /** Reads the instance URL from the deployment configuration. */
  public NominatimCitySearch(ObjectMapper mapper, @Value("${TRIP_NOMINATIM_SEARCH_URL:}") String endpoint) {
    this.mapper = mapper;
    this.endpoint = endpoint.strip();
  }

  /** Searches only after an explicit action in the trip editor. */
  @Override
  public synchronized List<City> search(String q, String language) {
    String query = q.strip().replaceAll("\\s+", " ");
    if (query.length() < 2 || query.length() > 120) return List.of();
    String lang = language.matches("[a-z]{2}") ? language : "ru";
    String key = lang + ":" + query.toLowerCase(Locale.ROOT);
    Instant now = Instant.now();
    var known = cache.get(key);
    if (known != null && now.isBefore(known.expires())) return known.cities();
    if (endpoint.isEmpty() || now.isBefore(nextRequest)) throw unavailable();
    nextRequest = now.plusSeconds(1);
    try {
      var url = URI.create(endpoint + (endpoint.contains("?") ? "&" : "?")
          + "format=jsonv2&limit=8&featureType=settlement&accept-language=" + lang
          + "&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
      var connection = (HttpURLConnection) url.toURL().openConnection();
      connection.setInstanceFollowRedirects(false);
      connection.setConnectTimeout(2500);
      connection.setReadTimeout(4000);
      connection.setRequestProperty("User-Agent", "CoDoc (https://www.codoc.site)");
      try {
        if (connection.getResponseCode() != 200) throw unavailable();
        try (var stream = connection.getInputStream()) {
          byte[] body = stream.readNBytes(128_001);
          if (body.length > 128_000) throw unavailable();
          var json = mapper.readTree(body);
          if (json == null || !json.isArray()) throw unavailable();
          var cities = new LinkedHashMap<String, City>();
          for (var item : json) {
            if (!SETTLEMENTS.contains(item.path("addresstype").asText())) continue;
            String label = item.path("display_name").asText("").strip();
            String name = item.path("name").asText("").strip();
            double lat = item.path("lat").asDouble(Double.NaN), lon = item.path("lon").asDouble(Double.NaN);
            if (name.isEmpty() || label.isEmpty() || !(Math.abs(lat) <= 85 && Math.abs(lon) <= 180)) continue;
            cities.putIfAbsent(label, new City(name, label, lat, lon));
          }
          var result = List.copyOf(cities.values());
          cache.put(key, new Cached(now.plusSeconds(86_400), result));
          if (cache.size() > 512) cache.remove(cache.keySet().iterator().next());
          return result;
        }
      } finally {
        connection.disconnect();
      }
    } catch (IOException | IllegalArgumentException e) {
      throw unavailable();
    }
  }

  private static ApiException unavailable() {
    return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "journey.trip.city-search-unavailable");
  }
}
