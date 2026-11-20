package ru.ticket.journey.infrastructure.hotel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.ticket.journey.domain.model.Trip;
import ru.ticket.journey.domain.service.HotelProvider.Query;

class TripHotelCatalogTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final Query query = new Query("Тверь", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 2), 2);

  @Test
  void callsTheConfiguredMcpToolWithDatesAndGuests() throws Exception {
    var captured = new AtomicReference<com.fasterxml.jackson.databind.JsonNode>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/mcp", exchange -> {
      try (exchange) {
        var request = mapper.readTree(exchange.getRequestBody());
        var method = request.path("method").asText();
        if (method.startsWith("notifications/")) { exchange.sendResponseHeaders(202, -1); return; }
        var result = method.equals("initialize")
            ? mapper.readTree("""
                {"protocolVersion":"2025-03-26","serverInfo":{"name":"hotel-fixture","version":"1"},"capabilities":{"tools":{}}}
                """)
            : mapper.readTree("""
                {"content":[],"structuredContent":{"hotels":[{"id":"live","city":"Тверь","name":"Hotel",
                  "cost":{"amount":3400,"currency":"RUB"},"freeCancellation":true}]}}
                """);
        if (method.equals("tools/call")) captured.set(request.path("params"));
        var envelope = mapper.createObjectNode().put("jsonrpc", "2.0");
        envelope.set("id", request.path("id"));
        envelope.set("result", result);
        byte[] body = mapper.writeValueAsBytes(envelope);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
      }
    });
    server.start();
    var catalog = new TripHotelCatalog(mapper, "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp",
        "hotel_fixture", "", Duration.ofSeconds(3), Duration.ofSeconds(5));
    try {
      var result = catalog.search(query);
      assertThat(result.provider()).isEqualTo(Trip.HotelProvider.OSTROVOK_MCP);
      assertThat(result.hotels()).singleElement().satisfies(hotel -> {
        assertThat(hotel.cost().amount()).isEqualByComparingTo("3400");
        assertThat(hotel.freeCancellation()).isTrue();
      });
      assertThat(captured.get().path("name").asText()).isEqualTo("hotel_fixture");
      assertThat(captured.get().path("arguments").path("checkIn").asText()).isEqualTo("2027-01-01");
      assertThat(captured.get().path("arguments").path("guests").asInt()).isEqualTo(2);
    } finally {
      catalog.destroy();
      server.stop(0);
    }
  }

  @Test
  void readsTheRegistryWithoutTreatingItsPricesAsCurrent(@TempDir Path directory) throws Exception {
    var file = directory.resolve("hotels.json");
    Files.writeString(file, """
        [{"id":"registry","city":"Тверь","name":"Hotel","cost":{"amount":3000,"currency":"RUB"}},
         {"id":"other","city":"Казань","name":"Other hotel"}]
        """);
    var catalog = new TripHotelCatalog(mapper, "", "", file.toString(), Duration.ofSeconds(3), Duration.ofSeconds(5));
    var result = catalog.search(query);
    assertThat(result.provider()).isEqualTo(Trip.HotelProvider.RUSSIAN_TOURISM);
    assertThat(result.hotels()).singleElement().satisfies(hotel -> assertThat(hotel.cost()).isNull());
    assertThatThrownBy(() -> new Query("Тверь", query.checkOut(), query.checkIn(), 2))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
