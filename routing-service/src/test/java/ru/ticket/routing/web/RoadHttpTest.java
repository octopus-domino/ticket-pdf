package ru.ticket.routing.web;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:routing-api-test;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop", "app.mcp.osm.enabled=false", "app.mcp.commons.enabled=false",
    "routing.providers.valhalla.enabled=false", "routing.providers.osrm.enabled=false", "routing.providers.brouter.enabled=false"})
class RoadHttpTest {
    private static final AtomicInteger calls = new AtomicInteger();
    private static HttpServer upstream;
    @Autowired TestRestTemplate http;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) throws IOException {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/brouter", exchange -> {
            calls.incrementAndGet();
            String json = """
                    {"features":[{"geometry":{"type":"LineString","coordinates":[[30,59],[30.01,59]]},
                    "properties":{"track-length":555,"total-time":300}}]}
                    """;
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var body = exchange.getResponseBody()) { body.write(bytes); }
        });
        upstream.start();
        registry.add("routing.providers.brouter-http.url", () -> "http://127.0.0.1:" + upstream.getAddress().getPort());
    }

    @AfterAll static void stopProvider() { if (upstream != null) upstream.stop(0); }

    @Test void returnsProviderGeometryThroughThePublicBoundary() {
        var result = post("{\"longitude\":30,\"latitude\":59}", "foot");
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).contains("\"distanceMeters\":555.0", "\"durationSeconds\":300.0", "[30.0,59.0]");
    }

    @Test void rejectsInvalidAndMissingCoordinatesBeforeContactingProvider() {
        int before = calls.get();
        for (String point : new String[]{"{}", "null", "{\"longitude\":181,\"latitude\":0}",
                "{\"longitude\":null,\"latitude\":59}"}) {
            var result = post(point, "car");
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(result.getBody()).contains("journey.route.invalid-route");
        }
        assertThat(post("{\"longitude\":30,\"latitude\":59}", "train").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(calls.get()).isEqualTo(before);
    }

    @org.springframework.boot.test.web.server.LocalServerPort
    int port;

    @Test void acceptsAndroidWebViewPreflightForRoutesAndPlaces() throws Exception {
        var client = java.net.http.HttpClient.newHttpClient();
        for (var endpoint : new String[]{"/api/routing/v1/road", "/api/walks/v1/places/themed"}) {
            var preflight = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + endpoint))
                    .header("Origin", "https://localhost")
                    .header("Access-Control-Request-Method", endpoint.endsWith("/road") ? "POST" : "GET")
                    .header("Access-Control-Request-Headers", "content-type, authorization")
                    .method("OPTIONS", java.net.http.HttpRequest.BodyPublishers.noBody()).build();
            var response = client.send(preflight, java.net.http.HttpResponse.BodyHandlers.discarding());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains("https://localhost");
        }
    }

    private ResponseEntity<String> post(String start, String mode) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {"start":%s,"end":{"longitude":30.01,"latitude":59},"mode":"%s"}
                """.formatted(start, mode);
        return http.postForEntity("/api/routing/v1/road", new HttpEntity<>(body, headers), String.class);
    }
}
