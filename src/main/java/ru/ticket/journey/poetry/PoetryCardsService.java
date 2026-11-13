package ru.ticket.journey.poetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import ru.ticket.common.ApiException;

@Service
public class PoetryCardsService {
  public record Input(double latitude, double longitude, String zone, String language, String mood, String audience, String seed) {}
  public record Card(String date, String title, String poem, String author, String authorId, String authorUrl, String country, String sourceUrl, String licenseUrl) {}
  public record Bundle(String language, String mood, String audience, List<Card> cards) {}
  public record Prepared(String id, Bundle bundle, String weatherSource, String weatherLicense) {}
  private final PoetryWeatherService weather;
  private final WikisourcePoems poems;
  private final PoetrySetRepository sets;
  private final ObjectMapper mapper;
  public PoetryCardsService(PoetryWeatherService weather, WikisourcePoems poems, PoetrySetRepository sets, ObjectMapper mapper) {
    this.weather = weather; this.poems = poems; this.sets = sets; this.mapper = mapper;
  }
  public Prepared prepare(Input input) {
    if (input == null || input.seed() != null && input.seed().length() > 100) throw ApiException.badRequest("journey.poetry.preferences");
    PoetrySelection.validate(input.language(), input.mood(), input.audience());
    var forecast = weather.forecast(input.latitude(), input.longitude(), input.zone());
    var selected = PoetrySelection.select(poems.poems(input.language(), input.mood(), input.audience()), forecast.days(),
        input.language(), input.mood(), input.audience(), input.seed());
    var cards = new ArrayList<Card>();
    for (int index = 0; index < 7; index++) {
      var poem = selected.get(index);
      cards.add(new Card(forecast.days().get(index).date(), poem.title(), poem.text(), poem.author(), poem.authorId(), poem.authorUrl(), poem.country(), poem.sourceUrl(), poem.licenseUrl()));
    }
    var bundle = new Bundle(input.language(), input.mood(), input.audience(), List.copyOf(cards));
    try {
      String payload = mapper.writeValueAsString(bundle);
      if (payload.length() > 40000) throw ApiException.badRequest("journey.poetry.not-enough");
      String id = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
      if (!sets.existsById(id)) {
        var row = new PoetrySet(); row.id = id; row.payload = payload; row.createdAt = Instant.now();
        try { sets.saveAndFlush(row); } catch (org.springframework.dao.DataIntegrityViolationException duplicate) { if (!sets.existsById(id)) throw duplicate; }
      }
      return new Prepared(id, bundle, forecast.source(), forecast.license());
    } catch (java.io.IOException | java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
  }
  public Bundle get(String id) {
    if (id == null || !id.matches("[a-f0-9]{64}")) throw ApiException.badRequest("journey.poetry.preferences");
    var row = sets.findById(id).orElseThrow(() -> ApiException.notFound("Poetry cards"));
    try { return mapper.readValue(row.payload, Bundle.class); } catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
  }
}
