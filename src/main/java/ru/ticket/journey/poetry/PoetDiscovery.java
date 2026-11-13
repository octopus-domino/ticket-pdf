package ru.ticket.journey.poetry;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import ru.ticket.common.ApiException;

/** Authors are discovered from live structured data. This is not an anthology or a hardcoded poet list. */
@Service
public class PoetDiscovery {
  public record Poet(String id, String name, String sourcePage, String country, int popularity) {}
  private record Cached(long until, List<Poet> poets) {}
  private static final Map<String,String> COUNTRIES = Map.of("ru", "wd:Q159", "en", "wd:Q145 wd:Q30", "zh", "wd:Q148",
      "pl", "wd:Q36", "it", "wd:Q38", "es", "wd:Q29", "nl", "wd:Q55", "fr", "wd:Q142", "de", "wd:Q183");
  private final Map<String,Cached> cache = new ConcurrentHashMap<>();
  private final PoetryRemoteJson remote;
  public PoetDiscovery(PoetryRemoteJson remote) { this.remote = remote; }
  public List<Poet> discover(String language) throws java.io.IOException {
    var countries = COUNTRIES.get(language); if (countries == null) throw ApiException.badRequest("journey.poetry.language");
    var found = cache.get(language); if (found != null && found.until > System.currentTimeMillis()) return found.poets;
    String query = """
        SELECT DISTINCT ?author ?authorLabel ?sourceName ?country ?pop WHERE {
          VALUES ?country { %s }
          ?author wdt:P106/wdt:P279* wd:Q49757; wdt:P570 ?death.
          FILTER(?death < "1901-01-01T00:00:00Z"^^xsd:dateTime)
          { ?author wdt:P27 ?country } UNION { ?author wdt:P19/wdt:P17 ?country }
          ?article schema:about ?author; schema:isPartOf <https://%s.wikisource.org/>; schema:name ?sourceName.
          ?author wikibase:sitelinks ?pop.
          SERVICE wikibase:label { bd:serviceParam wikibase:language "%s,en". }
        } ORDER BY DESC(?pop) LIMIT 12
        """.formatted(countries, language, language);
    var payload = remote.get("https://query.wikidata.org/sparql?format=json&query=" + encode(query), 250000, 20000);
    var poets = new ArrayList<Poet>(); var seen = new HashSet<String>();
    for (var item : payload.path("results").path("bindings")) {
      String id = item.path("author").path("value").asText("").replaceFirst("^https?://www\\.wikidata\\.org/entity/", "");
      String name = item.path("authorLabel").path("value").asText(""), source = item.path("sourceName").path("value").asText("");
      String country = item.path("country").path("value").asText("").replaceFirst("^https?://www\\.wikidata\\.org/entity/", "");
      if (!id.matches("Q[1-9][0-9]{0,15}") || !seen.add(id) || name.isBlank() || name.length() > 300 || source.isBlank()
          || source.length() > 400 || !country.matches("Q[1-9][0-9]{0,15}")) continue;
      poets.add(new Poet(id, name, source, country, item.path("pop").path("value").asInt(0)));
    }
    if (poets.isEmpty()) throw new java.io.IOException("No verified poets were returned");
    var result = List.copyOf(poets); cache.put(language, new Cached(System.currentTimeMillis() + Duration.ofHours(24).toMillis(), result)); return result;
  }
  static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
