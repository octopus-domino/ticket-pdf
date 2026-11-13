package ru.ticket.journey.poetry;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.ticket.common.ApiException;

/** Regional forecast cache uses rounded coordinates, not a history of precise user locations. */
@Service
public class PoetryWeatherService {
  public record Day(String date, String symbol, double temperature) {}
  public record Forecast(String zone, List<Day> days, Instant fetchedAt, String source, String license) {}
  private record Sample(Instant time, String symbol, double temperature) {}
  private record Cached(long until, List<Sample> samples, Instant fetchedAt) {}
  private final Map<String,Cached> cache = new LinkedHashMap<>(64, .75f, true);
  private final Semaphore network = new Semaphore(1);
  private final PoetryRemoteJson remote;
  private final String endpoint;
  public PoetryWeatherService(PoetryRemoteJson remote,
      @Value("${app.poetry.forecast-url:https://api.met.no/weatherapi/locationforecast/2.0/compact}") String endpoint) {
    this.remote = remote; this.endpoint = endpoint;
  }
  public Forecast forecast(double latitude, double longitude, String zone) {
    ZoneId timeZone;
    try { timeZone = ZoneId.of(zone); } catch (RuntimeException ex) { throw ApiException.badRequest("journey.poetry.location"); }
    if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180)
      throw ApiException.badRequest("journey.poetry.location");
    String point = String.format(Locale.ROOT, "lat=%.2f&lon=%.2f", latitude, longitude);
    var found = cached(point);
    if (found != null) {
      try { return select(found.samples, timeZone, Instant.now(), found.fetchedAt); }
      catch (DateTimeException ex) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "journey.poetry.weather-unavailable"); }
    }
    if (!network.tryAcquire()) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "journey.route.search-busy");
    try {
      found = cached(point); if (found != null) return select(found.samples, timeZone, Instant.now(), found.fetchedAt);
      Instant now = Instant.now();
      var samples = readSamples(remote.get(endpoint + "?" + point, 1_000_000));
      synchronized (cache) {
        // MET asks for at least three hours between identical non-Nordic requests.
        cache.put(point, new Cached(System.currentTimeMillis() + Duration.ofHours(3).toMillis(), samples, now));
        while (cache.size() > 64) cache.remove(cache.keySet().iterator().next());
      }
      return select(samples, timeZone, now, now);
    } catch (java.io.IOException | DateTimeException ex) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "journey.poetry.weather-unavailable");
    } finally { network.release(); }
  }
  private Cached cached(String key) {
    synchronized (cache) { var value = cache.get(key); return value != null && value.until > System.currentTimeMillis() ? value : null; }
  }
  static Forecast parse(JsonNode payload, ZoneId zone, Instant now) {
    return select(readSamples(payload), zone, now, now);
  }
  private static List<Sample> readSamples(JsonNode payload) {
    var samples = new ArrayList<Sample>();
    for (var item : payload.path("properties").path("timeseries")) {
      if (samples.size() >= 512) throw new DateTimeException("Too many forecast samples");
      var data = item.path("data");
      String symbol = data.path("next_1_hours").path("summary").path("symbol_code").asText(
          data.path("next_6_hours").path("summary").path("symbol_code").asText(""));
      double temperature = data.path("instant").path("details").path("air_temperature").asDouble(Double.NaN);
      if (!symbol.isBlank() && symbol.length() <= 80 && Double.isFinite(temperature))
        samples.add(new Sample(Instant.parse(item.path("time").asText()), symbol, temperature));
    }
    return List.copyOf(samples);
  }
  private static Forecast select(List<Sample> samples, ZoneId zone, Instant now, Instant fetchedAt) {
    var days = new ArrayList<Day>(); var today = now.atZone(zone).toLocalDate();
    for (int index = 0; index < 7; index++) {
      LocalDate date = today.plusDays(index); Sample selected = null; long nearest = Long.MAX_VALUE;
      var noon = date.atTime(12, 0).atZone(zone).toInstant();
      for (var item : samples) {
        var instant = item.time();
        if (!instant.atZone(zone).toLocalDate().equals(date)) continue;
        long distance = Math.abs(Duration.between(noon, instant).getSeconds());
        if (distance < nearest) { selected = item; nearest = distance; }
      }
      if (selected == null) throw new DateTimeException("Seven regional forecast days are unavailable");
      days.add(new Day(date.toString(), selected.symbol(), selected.temperature()));
    }
    return new Forecast(zone.getId(), List.copyOf(days), fetchedAt, "https://www.met.no/en", "https://creativecommons.org/licenses/by/4.0/");
  }
}
