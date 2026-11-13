package ru.ticket.journey;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Optional server-side source. Credentials and upstream URLs never reach the browser or logs. */
@Service
public class WikimapiaPlaces {
  private final String key;
  private final ObjectMapper mapper;
  private final Map<String, List<PlaceRecommendationsController.Place>> cache = new LinkedHashMap<>();
  private long expires;
  public WikimapiaPlaces(ObjectMapper mapper, @Value("${app.journeys.wikimapia-key:}") String key) { this.mapper = mapper; this.key = key; }
  public boolean enabled() { return !key.isBlank(); }
  public synchronized List<PlaceRecommendationsController.Place> search(double lon, double lat, String language) throws java.io.IOException {
    String locale = Set.of("ru","en","de","fr","it","es","pl","zh","nl").contains(language) ? language : "en";
    String cacheKey = String.format(Locale.ROOT, "%.3f:%.3f:%s", lon, lat, locale);
    if (System.currentTimeMillis() > expires) { cache.clear(); expires = System.currentTimeMillis() + 600000; }
    if (cache.containsKey(cacheKey)) return cache.get(cacheKey);
    String query = "https://api.wikimapia.org/?function=box&format=json&count=20&language=" + locale
        + "&bbox=" + (lon-.04) + "," + (lat-.025) + "," + (lon+.04) + "," + (lat+.025)
        + "&category=53756&key=" + encode(key);
    var connection = (HttpURLConnection) URI.create(query).toURL().openConnection();
    try {
      connection.setConnectTimeout(3000); connection.setReadTimeout(7000); connection.setInstanceFollowRedirects(false);
      connection.setRequestProperty("User-Agent", "CoDoc/1.0 (https://codoc.site)");
      if (connection.getResponseCode() != 200) throw new java.io.IOException("Wikimapia unavailable");
      byte[] bytes; try (var stream = connection.getInputStream()) { bytes = stream.readNBytes(500001); }
      if (bytes.length > 500000) throw new java.io.IOException("Wikimapia response too large");
      var payload = mapper.readTree(bytes);
      if (payload.has("error") || !payload.path("folder").isArray()) throw new java.io.IOException("Wikimapia rejected request");
      var result = new ArrayList<PlaceRecommendationsController.Place>();
      for (var item : payload.path("folder")) {
        String id = item.path("id").asText(""), title = org.jsoup.Jsoup.parse(item.path("name").asText("")).text();
        var point = item.path("location"); double x = point.path("lon").asDouble(Double.NaN), y = point.path("lat").asDouble(Double.NaN);
        if (!id.matches("[0-9]{1,20}") || title.isBlank() || title.length()>250 || !Double.isFinite(x) || !Double.isFinite(y)
            || Math.abs(x-lon)>.06 || Math.abs(y-lat)>.04) continue;
        result.add(new PlaceRecommendationsController.Place("wikimapia-"+id,title,x,y,"https://wikimapia.org/"+id,mapper.createObjectNode(),"",null));
      }
      if (cache.size()>=64) cache.remove(cache.keySet().iterator().next());
      var value = List.copyOf(result); cache.put(cacheKey,value); return value;
    } finally { connection.disconnect(); }
  }
  private static String encode(String text) { return URLEncoder.encode(text, StandardCharsets.UTF_8); }
}
