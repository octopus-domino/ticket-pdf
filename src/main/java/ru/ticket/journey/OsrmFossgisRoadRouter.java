package ru.ticket.journey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.ticket.common.ApiException;

@Service
@ConditionalOnProperty(name = "app.journeys.osrm.enabled", havingValue = "true", matchIfMissing = true)
public class OsrmFossgisRoadRouter implements RoadRouter {
    private static final Logger log = LoggerFactory.getLogger(OsrmFossgisRoadRouter.class);
    private static final Duration RATE_LIMIT_COOLDOWN = Duration.ofMinutes(5);

    private final ObjectMapper mapper;
    private final String endpoint;
    private final HttpClient http =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

    private final AtomicLong unavailableUntil = new AtomicLong(0);

    public OsrmFossgisRoadRouter(
            ObjectMapper mapper,
            @Value("${app.journeys.osrm-url:https://routing.openstreetmap.de}") String endpoint) {
        this.mapper = mapper;
        this.endpoint = endpoint.replaceAll("/+$", "");
    }

    @Override
    public Road route(Point start, Point end, String mode) {
        if (start == null || end == null) throw ApiException.badRequest("journey.route.invalid-route");
        if (!Double.isFinite(start.longitude())
                || !Double.isFinite(start.latitude())
                || !Double.isFinite(end.longitude())
                || !Double.isFinite(end.latitude())
                || Math.abs(start.longitude()) >= 180
                || Math.abs(start.latitude()) > 85
                || Math.abs(end.longitude()) >= 180
                || Math.abs(end.latitude()) > 85)
            throw ApiException.badRequest("journey.route.invalid-route");
        if (mode == null || !Set.of("car", "foot").contains(mode))
            throw ApiException.badRequest("journey.route.invalid-route");

        long now = System.currentTimeMillis();
        long blockedUntil = unavailableUntil.get();
        if (now < blockedUntil) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "journey.route.rate-limited");
        }

        String profile = mode.equals("car") ? "routed-car" : "routed-foot";
        String coordinates =
                String.format(
                        Locale.ROOT,
                        "%.6f,%.6f;%.6f,%.6f",
                        start.longitude(),
                        start.latitude(),
                        end.longitude(),
                        end.latitude());
        String url =
                endpoint
                        + "/"
                        + profile
                        + "/route/v1/driving/"
                        + coordinates
                        + "?overview=full&geometries=geojson&steps=false";

        try {
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(12))
                            .header("User-Agent", "CoDoc/1.0 (https://codoc.site)")
                            .GET()
                            .build();

            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            int code = response.statusCode();

            if (code == 429) {
                unavailableUntil.set(System.currentTimeMillis() + RATE_LIMIT_COOLDOWN.toMillis());
                log.warn("osrm.rate_limited cooldown_min={}", RATE_LIMIT_COOLDOWN.toMinutes());
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "journey.route.rate-limited");
            }
            if (code >= 500) {
                log.warn("osrm.server_error status={}", code);
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "journey.route.unavailable");
            }
            if (code >= 400) {
                log.warn("osrm.client_error status={}", code);
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"journey.route.no-path");
            }

            JsonNode root = mapper.readTree(response.body());
            if (!"Ok".equals(root.path("code").asText())) {
                log.warn("osrm.no_route");
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"journey.route.no-path");
            }

            var routes = root.path("routes");
            if (!routes.isArray() || routes.isEmpty()) throw new IOException("No routes");
            var route = routes.get(0);
            double distance = route.path("distance").asDouble(0);
            double duration = route.path("duration").asDouble(0);
            if (distance <= 0 || duration <= 0) throw new IOException("Invalid route");

            var coords = new ArrayList<List<Double>>();
            for (var point : route.path("geometry").path("coordinates")) {
                double lon = point.path(0).asDouble(Double.NaN);
                double lat = point.path(1).asDouble(Double.NaN);
                if (!Double.isFinite(lon) || !Double.isFinite(lat)) continue;
                coords.add(List.of(lon, lat));
                if (coords.size() > 30000) throw new IOException("Route geometry too large");
            }
            if (coords.size() < 2) throw new IOException("Invalid geometry");

            log.info(
                    "osrm.route mode={} distance_m={} duration_s={} points={}",
                    mode,
                    Math.round(distance),
                    Math.round(duration),
                    coords.size());
            return new Road(List.copyOf(coords), distance, duration);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "journey.route.unavailable");
        } catch (IOException ex) {
            log.warn("osrm.io_failed msg={}", ex.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "journey.route.unavailable");
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
