package ru.ticket.journey.walk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.ticket.mcp.OsmMcpClient;

@Component
public class PhotonPoiSource {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PhotonPoiSource.class);
    private static final Set<String> PHOTON_LANGS = Set.of("de", "en", "fr");

    private final ObjectMapper mapper;
    private final WalkRemote remote;
    private final String endpoint;
    private final Semaphore permit = new Semaphore(1);
    private long nextRequest;
    private final WalkCache<String, List<OsmMcpClient.Poi>> cache =
            new WalkCache<>(64, Duration.ofMinutes(10));

    public PhotonPoiSource(
            ObjectMapper mapper,
            WalkRemote remote,
            @Value("${app.journeys.photon-url:https://photon.komoot.io/api/}") String endpoint) {
        this.mapper = mapper;
        this.remote = remote;
        this.endpoint = endpoint;
    }

    public List<OsmMcpClient.Poi> search(
            double lon, double lat, int radius,
            WalkThemeProperties.Theme theme, String language) throws IOException {

        double dy = radius / 111195.0;
        double dx = dy / Math.cos(Math.toRadians(lat));
        String bbox = String.format(Locale.ROOT, "%.6f,%.6f,%.6f,%.6f",
                lon - dx, lat - dy, lon + dx, lat + dy);

        String key = String.format(Locale.ROOT, "%.4f:%.4f:%d:%s:%s",
                lon, lat, radius, theme.tags(), language);
        var cached = cache.get(key);
        if (cached != null) return cached;

        if (!permit.tryAcquire()) throw new IOException("Photon busy");
        try {
            var result = new LinkedHashMap<String, OsmMcpClient.Poi>();
            String langParam = PHOTON_LANGS.contains(language) ? language : null;

            // Photon: osm_tag устарел, используем include=osm.amenity.bar
            for (String tag : theme.tags()) {
                if (tag.contains("=*")) continue;

                // amenity=bar -> osm.amenity.bar
                String category = toCategory(tag);
                if (category == null) continue;

                try {
                    // q не передаём — при наличии include он не обязателен
                    fetch(buildUrl(bbox, null, category, langParam), result);
                } catch (IOException unavailable) {
                    log.debug("photon.include_failed category={} msg={}", category, unavailable.getMessage());
                }
                if (result.size() >= 20) break;
            }

            if (result.isEmpty()) {
                log.info("photon.empty tags={} lang={} — no include matches", theme.tags(), language);
            }

            var value = List.copyOf(result.values());
            cache.put(key, value);
            log.info("photon.search tags={} lang={} effective_lang={} result={}",
                    theme.tags(), language, langParam == null ? "default" : langParam, value.size());
            return value;
        } finally {
            permit.release();
        }
    }

    /**
     * amenity=bar -> osm.amenity.bar
     * tourism=museum -> osm.tourism.museum
     * historic=ruins -> osm.historic.ruins
     */
    private static String toCategory(String tag) {
        int eq = tag.indexOf('=');
        if (eq <= 0 || eq == tag.length() - 1) return null;
        String key = tag.substring(0, eq).trim();
        String value = tag.substring(eq + 1).trim();
        if (!key.matches("[a-z_]{1,40}") || !value.matches("[a-z_0-9]{1,60}")) return null;
        return "osm." + key + "." + value;
    }

    private String buildUrl(String bbox, String q, String includeCategory, String lang) {
        var url = new StringBuilder(endpoint);
        url.append("?limit=20");
        url.append("&bbox=").append(URLEncoder.encode(bbox, StandardCharsets.UTF_8));
        if (q != null && !q.isBlank()) {
            url.append("&q=").append(URLEncoder.encode(q, StandardCharsets.UTF_8));
        }
        if (includeCategory != null && !includeCategory.isBlank()) {
            // include=osm.amenity.bar — без URL-encode, точка допустима в query string
            url.append("&include=").append(includeCategory);
        }
        if (lang != null) {
            url.append("&lang=").append(URLEncoder.encode(lang, StandardCharsets.UTF_8));
        }
        return url.toString();
    }

    private void fetch(String url, Map<String, OsmMcpClient.Poi> result) throws IOException {
        synchronized (this) {
            long now = System.currentTimeMillis();
            if (now < nextRequest) {
                long wait = nextRequest - now;
                try { Thread.sleep(wait); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("interrupted"); }
            }
            nextRequest = System.currentTimeMillis() + 200;
        }
        var payload = remote.get(url);
        for (var feature : payload.path("features")) {
            var poi = parse(feature);
            if (poi != null) result.putIfAbsent(poi.id(), poi);
        }
    }

    private OsmMcpClient.Poi parse(JsonNode feature) {
        var props = feature.path("properties");
        String osmType = props.path("osm_type").asText("");
        long osmId = props.path("osm_id").asLong(0);
        String type = switch (osmType) {
            case "N" -> "node";
            case "W" -> "way";
            case "R" -> "relation";
            default -> "";
        };
        if (type.isEmpty() || osmId <= 0) return null;
        String id = type + "/" + osmId;
        String name = props.path("name").asText("");
        var coords = feature.path("geometry").path("coordinates");
        double lon = coords.path(0).asDouble(Double.NaN);
        double lat = coords.path(1).asDouble(Double.NaN);
        if (name.isBlank() || name.length() > 250 || !Double.isFinite(lon) || !Double.isFinite(lat)) return null;

        var tags = new LinkedHashMap<String, String>();
        String k = props.path("osm_key").asText("");
        String v = props.path("osm_value").asText("");
        if (!k.isBlank() && !v.isBlank()) tags.put(k, v);

        for (String extra : new String[]{
                "amenity", "shop", "tourism", "historic", "cuisine",
                "office", "leisure", "building", "highway", "military", "man_made"}) {
            String value = props.path(extra).asText("");
            if (!value.isBlank() && value.length() <= 500) tags.putIfAbsent(extra, value);
        }

        return new OsmMcpClient.Poi(id, name, lon, lat, Map.copyOf(tags));
    }
}