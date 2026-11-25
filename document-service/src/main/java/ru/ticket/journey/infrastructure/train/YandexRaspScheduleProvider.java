package ru.ticket.journey.infrastructure.train;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.ticket.journey.domain.service.TrainScheduleProvider;

/** Yandex schedule adapter with bounded requests, cache and a rolling daily quota. */
@Component
public class YandexRaspScheduleProvider implements TrainScheduleProvider {
  private static final Duration FRESHNESS = Duration.ofHours(1);
  private static final Duration QUOTA_WINDOW = Duration.ofDays(1);
  private static final int QUOTA = 500;
  private static final int MAX_BODY = 1_000_000;
  private static final int MAX_DIRECTORY = 48_000_000;
  private static final String BASE = "https://api.rasp.yandex-net.ru/v3.0/";
  private final LinkedHashMap<Query, Entry> cache = new LinkedHashMap<>(128, .75f, true);
  private final ArrayDeque<Instant> requests = new ArrayDeque<>();
  private final ObjectMapper mapper;
  private final String apiKey;
  private final Clock clock;
  private Instant retryAfter = Instant.EPOCH;
  private Instant directoryRefresh = Instant.EPOCH;
  private List<Place> directory = List.of();
  private record Entry(Instant loaded, List<Train> trains) {}

  /** Reads the API key from private runtime configuration. */
  public YandexRaspScheduleProvider(ObjectMapper mapper, @Value("${app.journeys.yandex-rasp-key:}") String apiKey) {
    this.mapper = mapper;
    this.apiKey = apiKey.strip();
    this.clock = Clock.systemUTC();
  }

  @Override
  public synchronized Result search(Query query) {
    var now = clock.instant();
    var previous = cache.get(query);
    if (previous != null && now.isBefore(previous.loaded().plus(FRESHNESS))) {
      return new Result(previous.trains(), false, true);
    }
    if (apiKey.isEmpty() || now.isBefore(retryAfter) || !reserve(now)) return fallback(previous, now);
    try {
      var trains = fetch(query, now);
      if (trains == null) return fallback(previous, now);
      cache.put(query, new Entry(now, trains));
      if (cache.size() > 512) cache.remove(cache.keySet().iterator().next());
      return new Result(trains, false, true);
    } catch (IOException unavailable) {
      return fallback(previous, now);
    }
  }

  @Override
  public synchronized List<Place> places(String text) {
    String query = normalize(text);
    if (query.length() < 2 || query.length() > 100 || apiKey.isEmpty()) return List.of();
    var now = clock.instant();
    if (!now.isBefore(directoryRefresh) && !now.isBefore(retryAfter) && reserve(now)) {
      directoryRefresh = now.plus(Duration.ofMinutes(10));
      try {
        var response = request(BASE + "stations_list/?format=json&lang=ru_RU", now, MAX_DIRECTORY);
        if (response != null) {
          directory = readPlaces(response);
          directoryRefresh = now.plus(Duration.ofDays(7));
        }
      } catch (IOException unavailable) {
        // An existing directory remains usable while the provider is temporarily unavailable.
      }
    }
    return directory.stream().filter(place -> normalize(place.title()).startsWith(query)).limit(12).toList();
  }

  private static String normalize(String text) {
    return text == null ? "" : text.strip().toLowerCase(Locale.ROOT).replace('ё', 'е');
  }

  static List<Place> readPlaces(JsonNode root) {
    var cities = new LinkedHashMap<String, Place>();
    var stations = new LinkedHashMap<String, Place>();
    for (var country : root.path("countries")) {
      for (var region : country.path("regions")) {
        String context = region.path("title").asText(country.path("title").asText());
        for (var settlement : region.path("settlements")) {
          String city = settlement.path("title").asText();
          boolean hasRailway = false;
          for (var station : settlement.path("stations")) {
            String transport = station.path("transport_type").asText();
            if (!"train".equals(transport) && !"suburban".equals(transport)) continue;
            hasRailway = true;
            String code = station.path("codes").path("yandex_code").asText();
            String title = station.path("title").asText();
            if (code.matches("s[0-9]{1,12}") && !title.isBlank()) {
              stations.putIfAbsent(code, new Place(code, title, city.isBlank() ? context : city));
            }
          }
          String code = settlement.path("codes").path("yandex_code").asText();
          if (hasRailway && code.matches("c[0-9]{1,12}") && !city.isBlank()) {
            cities.putIfAbsent(code, new Place(code, city, context));
          }
        }
      }
    }
    var places = new ArrayList<>(cities.values());
    places.addAll(stations.values());
    return List.copyOf(places);
  }

  private boolean reserve(Instant now) {
    while (!requests.isEmpty() && !requests.peekFirst().plus(QUOTA_WINDOW).isAfter(now)) requests.removeFirst();
    if (requests.size() >= QUOTA) return false;
    requests.addLast(now);
    return true;
  }

  private List<Train> fetch(Query query, Instant now) throws IOException {
    var response = request(BASE + "search/?from=" + query.from() + "&to=" + query.to() + "&date=" + query.date()
        + "&transport_types=train&format=json&lang=ru_RU&limit=30", now, MAX_BODY);
    return response == null ? null : readTrains(response);
  }

  private JsonNode request(String url, Instant now, int maxBody) throws IOException {
    var endpoint = URI.create(url);
    var connection = (HttpURLConnection) endpoint.toURL().openConnection();
    connection.setInstanceFollowRedirects(false);
    connection.setConnectTimeout(3000);
    connection.setReadTimeout(7000);
    connection.setRequestProperty("Authorization", apiKey);
    connection.setRequestProperty("Accept-Encoding", "gzip");
    try {
      int status = connection.getResponseCode();
      if (status == 429) retryAfter = now.plus(FRESHNESS);
      if (status != 200) return null;
      try (var received = connection.getInputStream();
          var stream = "gzip".equalsIgnoreCase(connection.getContentEncoding()) ? new GZIPInputStream(received) : received) {
        var body = stream.readNBytes(maxBody + 1);
        if (body.length > maxBody) throw new IOException("Yandex response too large");
        return mapper.readTree(body);
      }
    } finally {
      connection.disconnect();
    }
  }

  static List<Train> readTrains(JsonNode root) throws IOException {
    if (root == null) throw new IOException("Invalid schedule response");
    var segments = root.path("segments");
    if (!segments.isArray()) throw new IOException("Invalid schedule response");
    var result = new ArrayList<Train>();
    for (var segment : segments) {
      try {
        var departure = OffsetDateTime.parse(segment.path("departure").asText());
        var arrival = OffsetDateTime.parse(segment.path("arrival").asText());
        if (arrival.isBefore(departure)) continue;
        var thread = segment.path("thread");
        result.add(new Train(thread.path("number").asText(), thread.path("title").asText(),
            segment.path("from").path("title").asText(), segment.path("to").path("title").asText(), departure, arrival));
      } catch (DateTimeParseException invalid) {
        // A malformed segment cannot hide valid trains in the same response.
      }
      if (result.size() >= 30) break;
    }
    return List.copyOf(result);
  }

  private static Result fallback(Entry entry, Instant now) {
    return entry != null && now.isBefore(entry.loaded().plus(QUOTA_WINDOW))
        ? new Result(entry.trains(), true, true) : new Result(List.of(), false, false);
  }
}
