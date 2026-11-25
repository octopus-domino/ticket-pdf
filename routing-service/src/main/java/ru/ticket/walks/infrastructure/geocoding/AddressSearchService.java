package ru.ticket.walks.infrastructure.geocoding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.ticket.walks.shared.ApiException;
import ru.ticket.walks.shared.RouteCoordinates;

/** Bounded shared cache and one provider request per second across all visitors. */
@Service
public class AddressSearchService {
  public record Address(String label, double longitude, double latitude) {}
  public record City(String city) {}

  private record Cached(long expires, List<Address> addresses) {}

  private final Map<String, Cached> cache = new LinkedHashMap<>(128, .75f, true);
  private final java.util.concurrent.Semaphore permit = new java.util.concurrent.Semaphore(1);
  private final ObjectMapper mapper;
  private final String endpoint;
  private final String reverseEndpoint;
  private long nextRequest;

  public AddressSearchService(
      ObjectMapper mapper,
      @Value("${app.journeys.geocoder-url:https://photon.komoot.io/api/}") String endpoint) {
    this.mapper = mapper;
    this.endpoint = endpoint;
    this.reverseEndpoint = endpoint.replaceFirst("/api/?$", "/reverse");
  }

  public List<Address> search(String query, String language) {
    String text = query == null ? "" : query.strip().replaceAll("\\s+", " ");
    if (text.length() < 3 || text.length() > 180) return List.of();
    String lang = providerLanguage(language);
    return lookup("search:" + lang + ":" + text.toLowerCase(Locale.ROOT),
        endpoint + "?limit=5&q=" + URLEncoder.encode(text, StandardCharsets.UTF_8) + langParameter(lang),
        AddressSearchService::parseAddresses);
  }

  public City cityAt(double longitude, double latitude, String language) {
    RouteCoordinates.validate(longitude, latitude);
    String lang = providerLanguage(language);
    String point = String.format(Locale.ROOT, "lat=%.5f&lon=%.5f", latitude, longitude);
    var response = lookup("city:" + lang + ":" + point,
        reverseEndpoint + "?" + point + "&limit=5&radius=6&layer=city&layer=locality" + langParameter(lang),
        features -> {
          for (var feature : features) {
            String city = cityName(feature.path("properties"));
            if (!city.isEmpty()) return List.of(new Address(city, longitude, latitude));
          }
          return List.of();
        });
    return new City(response.isEmpty() ? null : response.get(0).label());
  }

  private static String providerLanguage(String language) {
    return language != null && Set.of("en", "de", "fr", "it").contains(language) ? language : "";
  }

  private static String langParameter(String language) {
    return language.isEmpty() ? "" : "&lang=" + language;
  }

  private static String cityName(JsonNode properties) {
    String name = properties.path("name").asText("").strip();
    if (!name.isEmpty() && "place".equals(properties.path("osm_key").asText())
        && Set.of("city", "town", "village", "hamlet", "municipality").contains(properties.path("osm_value").asText())) {
      return name;
    }
    String city = properties.path("city").asText("").strip();
    return !city.isEmpty() ? city : "city".equals(properties.path("type").asText()) ? name : "";
  }

  private List<Address> lookup(String key, String url, Function<JsonNode, List<Address>> parse) {
    if (!permit.tryAcquire())
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "journey.route.search-busy");
    try {
      long now = System.currentTimeMillis();
      Cached found = cache.get(key);
      if (found != null && found.expires > now) return found.addresses;
      if (now < nextRequest)
        throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "journey.route.search-busy");
      nextRequest = now + 1100;
      var result = List.copyOf(parse.apply(features(url)));
      cache.put(key, new Cached(now + Duration.ofHours(6).toMillis(), result));
      while (cache.size() > 512) cache.remove(cache.keySet().iterator().next());
      return result;
    } finally {
      permit.release();
    }
  }

  private JsonNode features(String url) {
    HttpURLConnection connection = null;
    try {
      connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
      connection.setConnectTimeout(4000);
      connection.setReadTimeout(8000);
      connection.setInstanceFollowRedirects(false);
      connection.setRequestProperty("User-Agent", "Codoc/1.0 (https://codoc.site)");
      connection.setRequestProperty("Accept", "application/json");
      if (connection.getResponseCode() != 200) throw new IllegalStateException();
      byte[] body;
      try (var stream = connection.getInputStream()) {
        body = stream.readNBytes(262145);
      }
      if (body.length > 262144) throw new IllegalStateException();
      return mapper.readTree(body).path("features");
    } catch (Exception ex) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "journey.route.search-unavailable");
    } finally {
      if (connection != null) connection.disconnect();
    }
  }

  private static List<Address> parseAddresses(JsonNode features) {
    List<Address> addresses = new ArrayList<>();
    for (JsonNode feature : features) {
      JsonNode coordinates = feature.path("geometry").path("coordinates");
      if (!coordinates.isArray() || coordinates.size() < 2) continue;
      double lon = coordinates.get(0).asDouble(Double.NaN),
          lat = coordinates.get(1).asDouble(Double.NaN);
      if (!Double.isFinite(lon)
          || !Double.isFinite(lat)
          || Math.abs(lon) > 180
          || Math.abs(lat) > 85) continue;
      JsonNode properties = feature.path("properties");
      Set<String> parts = new LinkedHashSet<>();
      String street = properties.path("street").asText("");
      String number = properties.path("housenumber").asText("");
      for (String part :
          List.of(
              properties.path("name").asText(""),
              (street + " " + number).strip(),
              properties.path("city").asText(""),
              properties.path("state").asText(""),
              properties.path("country").asText(""))) if (!part.isBlank()) parts.add(part);
      String label = String.join(", ", parts);
      if (!label.isBlank())
        addresses.add(new Address(label.substring(0, Math.min(300, label.length())), lon, lat));
      if (addresses.size() == 5) break;
    }
    return addresses;
  }
}
