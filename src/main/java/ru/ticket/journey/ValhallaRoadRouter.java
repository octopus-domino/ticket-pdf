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
import java.util.Map;
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
@ConditionalOnProperty(name = "app.journeys.valhalla.enabled", havingValue = "true", matchIfMissing = true)
public class ValhallaRoadRouter implements RoadRouter {
    private static final Logger log = LoggerFactory.getLogger(ValhallaRoadRouter.class);
    private static final Duration RATE_LIMIT_COOLDOWN = Duration.ofMinutes(5);

    private final ObjectMapper mapper;
    private final String endpoint;
    private final HttpClient http =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

    private final AtomicLong unavailableUntil = new AtomicLong(0);

    public ValhallaRoadRouter(
            ObjectMapper mapper,
            @Value("${app.journeys.valhalla-url:https://valhalla1.openstreetmap.de}") String endpoint) {
        this.mapper = mapper;
        this.endpoint = endpoint.replaceAll("/+$", "");
    }

    @Override
    public Road route(Point start, Point end, String mode) {
        validate(start);
        validate(end);
        if (mode == null || !Set.of("car", "foot").contains(mode))
            throw ApiException.badRequest("journey.route.invalid-route");
        if (distance(start, end) > (mode.equals("car") ? 1_500_000 : 400_000))
            throw ApiException.badRequest(
                    mode.equals("car") ? "journey.route.too-long" : "journey.route.walk-too-long");

        long now = System.currentTimeMillis();
        long blockedUntil = unavailableUntil.get();
        if (now < blockedUntil) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "journey.route.rate-limited");
        }

        String costing = mode.equals("car") ? "auto" : "pedestrian";

        try {
            Map<String, Object> body =
                    Map.of(
                            "locations",
                            List.of(
                                    Map.of("lat", start.latitude(), "lon", start.longitude()),
                                    Map.of("lat", end.latitude(), "lon", end.longitude())),
                            "costing",
                            costing,
                            "directions_options",
                            Map.of("units", "kilometers"));
            byte[] payload = mapper.writeValueAsBytes(body);

            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(endpoint + "/route"))
                            .timeout(Duration.ofSeconds(8))
                            .header("Content-Type", "application/json")
                            .header("User-Agent", "CoDoc/1.0 (https://codoc.site)")
                            .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                            .build();

            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            int code = response.statusCode();

            if (code == 429) {
                unavailableUntil.set(System.currentTimeMillis() + RATE_LIMIT_COOLDOWN.toMillis());
                log.warn("valhalla.rate_limited cooldown_min={}", RATE_LIMIT_COOLDOWN.toMinutes());
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "journey.route.rate-limited");
            }
            if (code >= 500) {
                log.warn("valhalla.server_error status={}", code);
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "journey.route.unavailable");
            }
            if (code >= 400) {
                log.warn("valhalla.client_error status={}", code);
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"journey.route.no-path");
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode trip = root.path("trip");
            if (!trip.isObject()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"journey.route.no-path");

            var summary = trip.path("summary");
            double lengthKm = summary.path("length").asDouble(0);
            double durationSec = summary.path("time").asDouble(0);
            if (!Double.isFinite(lengthKm) || lengthKm <= 0) throw new IOException("Invalid route length");
            if (durationSec <= 0) durationSec = lengthKm * 1000 / (mode.equals("foot") ? 1.4 : 13.9);

            var legs = trip.path("legs");
            if (!legs.isArray() || legs.isEmpty()) throw new IOException("No legs in route");
            String shape = legs.get(0).path("shape").asText("");
            if (shape.isBlank()) throw new IOException("No shape in leg");
            List<List<Double>> coords = decodePolyline6(shape);
            if (coords.size() < 2) throw new IOException("Invalid shape");

            return new Road(List.copyOf(coords), lengthKm * 1000, durationSec);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "journey.route.unavailable");
        } catch (IOException ex) {
            log.warn("valhalla.io_failed msg={}", ex.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "journey.route.unavailable");
        }
    }

    static List<List<Double>> decodePolyline6(String encoded) {
        var result = new ArrayList<List<Double>>();
        int index = 0, lat = 0, lon = 0;
        while (index < encoded.length()) {
            int shift = 0, b, resultLat = 0;
            do {
                b = encoded.charAt(index++) - 63;
                resultLat |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((resultLat & 1) != 0 ? ~(resultLat >> 1) : (resultLat >> 1));
            lat += dlat;

            shift = 0;
            int resultLon = 0;
            do {
                b = encoded.charAt(index++) - 63;
                resultLon |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlon = ((resultLon & 1) != 0 ? ~(resultLon >> 1) : (resultLon >> 1));
            lon += dlon;

            result.add(List.of(lon / 1e6, lat / 1e6));
        }
        return result;
    }

    static void validate(Point point) {
        if (point == null
                || !Double.isFinite(point.longitude())
                || !Double.isFinite(point.latitude())
                || Math.abs(point.longitude()) >= 180
                || Math.abs(point.latitude()) > 85)
            throw ApiException.badRequest("journey.route.invalid-route");
    }

    private static double distance(Point a, Point b) {
        double lat = Math.toRadians(b.latitude() - a.latitude()),
                lon = Math.toRadians(b.longitude() - a.longitude());
        double h =
                Math.pow(Math.sin(lat / 2), 2)
                        + Math.cos(Math.toRadians(a.latitude()))
                        * Math.cos(Math.toRadians(b.latitude()))
                        * Math.pow(Math.sin(lon / 2), 2);
        return 12_742_000 * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
