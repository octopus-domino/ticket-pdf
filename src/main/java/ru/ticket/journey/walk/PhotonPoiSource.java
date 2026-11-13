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

/**
 * Photon (photon.komoot.io) fallback when the configured Overpass mirror returns empty.
 *
 * <p>Photon only accepts lang=default|de|en|fr; everything else is a 400. We map our ISO codes
 * to the accepted set and drop the param otherwise. Photon also requires either a text
 * {@code q} or include/exclude filters, so every call includes a {@code q} even when
 * {@code osm_tag} is used. bbox must use '.' as decimal separator.
 */
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

        String key = String.format(Locale.ROOT, "%.4f:%.4f:%d:%s:%s", lon, lat, radius, theme.tags(), language);
        var cached = cache.get(key);
        if (cached != null) return cached;

        if (!permit.tryAcquire()) throw new IOException("Photon busy");
        try {
            var result = new LinkedHashMap<String, OsmMcpClient.Poi>();
            String langParam = PHOTON_LANGS.contains(language) ? language : null;

            for (String tag : theme.tags()) {
                if (tag.contains("=*")) continue;
                String photonTag = tag.replace('=', ':');
                String q = ""; // The OSM tag is already a complete query; do not require an English name.
                try {
                    fetch(buildUrl(bbox, q, photonTag, langParam), result);
                } catch (IOException unavailable) {
                    log.debug("photon.osm_tag_failed tag={} q={} msg={}", photonTag, q, unavailable.getMessage());
                }
                if (result.size() >= 20) break;
            }

            if (result.isEmpty()) {
                String query = queryFor(theme.tags());
                if (query != null && !query.isBlank()) {
                    try {
                        fetch(buildUrl(bbox, query, null, langParam), result);
                    } catch (IOException unavailable) {
                        log.debug("photon.q_failed q={} msg={}", query, unavailable.getMessage());
                    }
                }
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

    private String buildUrl(String bbox, String q, String osmTag, String lang) {
        var url = new StringBuilder(endpoint);
        url.append("?limit=20");
        url.append("&bbox=").append(URLEncoder.encode(bbox, StandardCharsets.UTF_8));
        url.append("&q=").append(URLEncoder.encode(q == null ? "" : q, StandardCharsets.UTF_8));
        if (osmTag != null && !osmTag.isBlank()) {
            url.append("&osm_tag=").append(URLEncoder.encode(osmTag, StandardCharsets.UTF_8));
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
        return new OsmMcpClient.Poi(id, name, lon, lat, Map.copyOf(tags));
    }

    private static String queryFor(List<String> tags) {
        for (String tag : tags) {
            if (tag.equals("amenity=place_of_worship")) return "church";
            if (tag.equals("amenity=bar")) return "bar";
            if (tag.equals("amenity=pub")) return "pub";
            if (tag.equals("amenity=biergarten")) return "beer garden";
            if (tag.equals("amenity=wine_bar")) return "wine bar";
            if (tag.equals("tourism=museum")) return "museum";
            if (tag.equals("tourism=gallery")) return "gallery";
            if (tag.equals("amenity=arts_centre")) return "art centre";
            if (tag.equals("amenity=theatre")) return "theatre";
            if (tag.equals("historic=ruins")) return "ruins";
            if (tag.equals("historic=monument")) return "monument";
            if (tag.equals("historic=aircraft_wreck")) return "wreck";
            if (tag.equals("amenity=monastery")) return "monastery";
            if (tag.equals("building=church")) return "church";
            if (tag.equals("building=cathedral")) return "cathedral";
            if (tag.equals("building=mosque")) return "mosque";
            if (tag.equals("military=bunker")) return "bunker";
            if (tag.equals("man_made=adit")) return "adit";
        }
        return null;
    }
}
