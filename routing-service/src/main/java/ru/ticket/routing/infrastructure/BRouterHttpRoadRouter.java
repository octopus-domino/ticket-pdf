package ru.ticket.routing.infrastructure;

import ru.ticket.routing.application.RoadProvider;
import ru.ticket.routing.domain.Road;
import ru.ticket.routing.domain.RoadRequest;
import ru.ticket.routing.domain.RoadRequest.Mode;
import ru.ticket.routing.domain.RoadRequest.Point;
import ru.ticket.routing.domain.RoutingFailure;
import static ru.ticket.routing.domain.RoutingFailure.Reason.*;
import org.springframework.core.annotation.Order;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@Order(10)
@ConditionalOnProperty(name = "routing.providers.brouter-http.enabled", havingValue = "true", matchIfMissing = true)
public class BRouterHttpRoadRouter implements RoadProvider {
    private static final Logger log = LoggerFactory.getLogger(BRouterHttpRoadRouter.class);
    private static final Duration RATE_LIMIT_COOLDOWN = Duration.ofMinutes(5);
    private static final double WALK_SPEED_MPS = 1.4;
    private static final double CAR_SPEED_MPS = 11.1;
    private static final long MIN_INTERVAL_MS = 700;
    private static final long RETRY_DELAY_MS = 2000;

    private final ObjectMapper mapper;
    private final String endpoint;
    private final HttpClient http =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

    private final Semaphore throttle = new Semaphore(1);
    private final AtomicLong unavailableUntil = new AtomicLong(0);
    private long lastCallAtMs = 0;

    public BRouterHttpRoadRouter(
            ObjectMapper mapper,
            @Value("${routing.providers.brouter-http.url:https://brouter.de}") String endpoint) {
        this.mapper = mapper;
        this.endpoint = endpoint.replaceAll("/+$", "");
    }

    @Override
    public Road calculate(RoadRequest route) {
        Point start = route.start();
        Point end = route.end();
        Mode mode = route.mode();
        long now = System.currentTimeMillis();
        long blockedUntil = unavailableUntil.get();
        if (now < blockedUntil) {
            throw new RoutingFailure(RATE_LIMITED);
        }

        String profile = mode == Mode.CAR ? "car-eco" : "shortest";

        if (!throttle.tryAcquire()) {
            try { throttle.acquire(); }
            catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RoutingFailure(UNAVAILABLE);
            }
        }

        try {
            long sinceLast = System.currentTimeMillis() - lastCallAtMs;
            if (sinceLast < MIN_INTERVAL_MS) {
                try { Thread.sleep(MIN_INTERVAL_MS - sinceLast); }
                catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RoutingFailure(UNAVAILABLE);
                }
            }

            Road road = callOnce(start, end, mode, profile);
            if (road == null) {
                log.info("brouter_http.empty_features_retry profile={} delay_ms={}", profile, RETRY_DELAY_MS);
                try { Thread.sleep(RETRY_DELAY_MS); }
                catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RoutingFailure(UNAVAILABLE);
                }
                road = callOnce(start, end, mode, profile);
            }
            if (road == null) {
                log.warn("brouter_http.empty_features mode={} profile={}", mode, profile);
                throw new RoutingFailure(NO_PATH);
            }
            return road;
        } finally {
            lastCallAtMs = System.currentTimeMillis();
            throttle.release();
        }
    }

    private Road callOnce(Point start, Point end, Mode mode, String profile) {
        String points =
                String.format(
                        Locale.ROOT,
                        "%.6f,%.6f|%.6f,%.6f",
                        start.longitude(), start.latitude(), end.longitude(), end.latitude());
        String url =
                endpoint
                        + "/brouter?lonlats=" + URLEncoder.encode(points, StandardCharsets.UTF_8)
                        + "&profile=" + profile
                        + "&alternativeidx=0&format=geojson";

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
                log.warn("brouter_http.rate_limited cooldown_min={}", RATE_LIMIT_COOLDOWN.toMinutes());
                throw new RoutingFailure(RATE_LIMITED);
            }
            if (code >= 500) {
                log.warn("brouter_http.server_error status={}", code);
                throw new RoutingFailure(UNAVAILABLE);
            }
            if (code >= 400) {
                log.warn("brouter_http.client_error status={}", code);
                throw new RoutingFailure(NO_PATH);
            }

            JsonNode root = mapper.readTree(response.body());
            var features = root.path("features");
            if (!features.isArray() || features.isEmpty()) {
                return null;
            }

            var feature = features.get(0);
            var geometry = feature.path("geometry");
            String type = geometry.path("type").asText();
            if (!type.equals("LineString") && !type.equals("MultiLineString"))
                throw new IOException("Invalid geometry: " + type);

            var coords = new ArrayList<List<Double>>();
            if (type.equals("LineString")) {
                appendCoords(geometry.path("coordinates"), coords);
            } else {
                for (var line : geometry.path("coordinates")) appendCoords(line, coords);
            }
            if (coords.size() < 2) throw new IOException("Not enough points");

            var props = feature.path("properties");
            double length = parseDoubleOrZero(props.path("track-length"));
            double duration = parseDoubleOrZero(props.path("total-time"));

            if (length <= 0) length = haversineMeters(coords);
            if (duration <= 0) duration = length / (mode == Mode.FOOT ? WALK_SPEED_MPS : CAR_SPEED_MPS);

            log.info(
                    "brouter_http.route mode={} profile={} distance_m={} duration_s={} points={}",
                    mode, profile, Math.round(length), Math.round(duration), coords.size());
            return new Road(List.copyOf(coords), length, duration);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RoutingFailure(UNAVAILABLE);
        } catch (IOException ex) {
            log.warn("brouter_http.io_failed msg={}", ex.getMessage());
            throw new RoutingFailure(UNAVAILABLE);
        }
    }

    private static void appendCoords(JsonNode array, List<List<Double>> out) throws IOException {
        if (!array.isArray()) return;
        for (var point : array) {
            double lon = point.path(0).asDouble(Double.NaN);
            double lat = point.path(1).asDouble(Double.NaN);
            if (!Double.isFinite(lon) || !Double.isFinite(lat)) continue;
            out.add(List.of(lon, lat));
            if (out.size() > 30_000) throw new IOException("Route geometry too large");
        }
    }

    private static double haversineMeters(List<List<Double>> coords) {
        double total = 0;
        for (int i = 1; i < coords.size(); i++) {
            double lon1 = coords.get(i - 1).get(0), lat1 = coords.get(i - 1).get(1);
            double lon2 = coords.get(i).get(0),     lat2 = coords.get(i).get(1);
            double dlat = Math.toRadians(lat2 - lat1);
            double dlon = Math.toRadians(lon2 - lon1);
            double h = Math.pow(Math.sin(dlat / 2), 2)
                    + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                    * Math.pow(Math.sin(dlon / 2), 2);
            total += 2 * 6_371_000 * Math.asin(Math.min(1, Math.sqrt(h)));
        }
        return total;
    }

    private static double parseDoubleOrZero(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return 0;
        if (node.isNumber()) return node.asDouble(0);
        String text = node.asText("").trim();
        if (text.isEmpty()) return 0;
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
