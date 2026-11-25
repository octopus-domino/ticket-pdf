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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@Order(20)
@ConditionalOnProperty(name = "routing.providers.valhalla.enabled", havingValue = "true", matchIfMissing = true)
public class ValhallaRoadRouter implements RoadProvider {
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
            @Value("${routing.providers.valhalla.url:https://valhalla1.openstreetmap.de}") String endpoint) {
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

        String costing = mode == Mode.CAR ? "auto" : "pedestrian";

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
                throw new RoutingFailure(RATE_LIMITED);
            }
            if (code >= 500) {
                log.warn("valhalla.server_error status={}", code);
                throw new RoutingFailure(UNAVAILABLE);
            }
            if (code >= 400) {
                log.warn("valhalla.client_error status={}", code);
                throw new RoutingFailure(NO_PATH);
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode trip = root.path("trip");
            if (!trip.isObject()) throw new RoutingFailure(NO_PATH);

            var summary = trip.path("summary");
            double lengthKm = summary.path("length").asDouble(0);
            double durationSec = summary.path("time").asDouble(0);
            if (!Double.isFinite(lengthKm) || lengthKm <= 0) throw new IOException("Invalid route length");
            if (durationSec <= 0) durationSec = lengthKm * 1000 / (mode == Mode.FOOT ? 1.4 : 13.9);

            var legs = trip.path("legs");
            if (!legs.isArray() || legs.isEmpty()) throw new IOException("No legs in route");
            String shape = legs.get(0).path("shape").asText("");
            if (shape.isBlank()) throw new IOException("No shape in leg");
            List<List<Double>> coords = decodePolyline6(shape);
            if (coords.size() < 2) throw new IOException("Invalid shape");

            return new Road(List.copyOf(coords), lengthKm * 1000, durationSec);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RoutingFailure(UNAVAILABLE);
        } catch (IOException ex) {
            log.warn("valhalla.io_failed msg={}", ex.getMessage());
            throw new RoutingFailure(UNAVAILABLE);
        }
    }

    static List<List<Double>> decodePolyline6(String encoded) throws IOException {
        var result = new ArrayList<List<Double>>();
        int index = 0, lat = 0, lon = 0;
        while (index < encoded.length()) {
            int shift = 0, b, resultLat = 0;
            do {
                if (index >= encoded.length() || shift > 30) throw new IOException("Invalid encoded shape");
                b = encoded.charAt(index++) - 63;
                if (b < 0 || b > 63) throw new IOException("Invalid encoded shape");
                resultLat |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((resultLat & 1) != 0 ? ~(resultLat >> 1) : (resultLat >> 1));
            lat += dlat;

            shift = 0;
            int resultLon = 0;
            do {
                if (index >= encoded.length() || shift > 30) throw new IOException("Invalid encoded shape");
                b = encoded.charAt(index++) - 63;
                if (b < 0 || b > 63) throw new IOException("Invalid encoded shape");
                resultLon |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlon = ((resultLon & 1) != 0 ? ~(resultLon >> 1) : (resultLon >> 1));
            lon += dlon;

            result.add(List.of(lon / 1e6, lat / 1e6));
        }
        return result;
    }
}
