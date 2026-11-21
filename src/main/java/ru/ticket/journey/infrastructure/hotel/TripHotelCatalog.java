package ru.ticket.journey.infrastructure.hotel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import ru.ticket.journey.domain.model.Trip;
import ru.ticket.journey.domain.service.HotelProvider;
import ru.ticket.mcp.McpClientFactory;

/** Uses a configured Ostrovok MCP adapter, then a local Russian tourism registry snapshot. */
@Component
@ConditionalOnProperty(name = "app.journeys.trip-planner-enabled", havingValue = "true")
public class TripHotelCatalog implements HotelProvider, DisposableBean {
  private final ObjectMapper mapper;
  private final String endpoint;
  private final String tool;
  private final String registry;
  private final Duration connectTimeout;
  private final Duration requestTimeout;
  private McpSyncClient client;
  private Instant retryAt = Instant.EPOCH;
  private List<Hotel> snapshot;

  /** Reads the provider settings without contacting the network at application startup. */
  public TripHotelCatalog(ObjectMapper mapper, @Value("${app.journeys.hotels.mcp-url:}") String endpoint,
      @Value("${app.journeys.hotels.mcp-tool:}") String tool, @Value("${app.journeys.hotels.registry-file:}") String registry,
      @Value("${app.journeys.hotels.connect-timeout:3s}") Duration connectTimeout,
      @Value("${app.journeys.hotels.request-timeout:15s}") Duration requestTimeout) {
    this.mapper = mapper;
    this.endpoint = endpoint.strip();
    this.tool = tool.strip();
    this.registry = registry.strip();
    this.connectTimeout = connectTimeout;
    this.requestTimeout = requestTimeout;
  }

  @Override
  public synchronized Result search(Query query) {
    if (!endpoint.isEmpty() && !tool.isEmpty() && !Instant.now().isBefore(retryAt)) {
      try {
        if (client == null) client = McpClientFactory.create(mapper, endpoint, connectTimeout, requestTimeout);
        if (!client.isInitialized()) client.initialize();
        var result = client.callTool(new McpSchema.CallToolRequest(tool, Map.of(
            "city", query.city(), "checkIn", query.checkIn().toString(), "checkOut", query.checkOut().toString(),
            "guests", query.guests(), "page", query.page())));
        if (Boolean.TRUE.equals(result.isError())) return fallback(query);
        var data = hotelResponse(result);
        var unique = new HashSet<String>();
        return new Result(true, Trip.HotelProvider.OSTROVOK_MCP,
            mapper.readerForListOf(Hotel.class).<List<Hotel>>readValue(data.path("hotels")).stream()
                .filter(TripHotelCatalog::usable).filter(hotel -> unique.add(hotel.id())).limit(50).toList(),
            data.path("catalogOnly").asBoolean(false), data.path("hasMore").asBoolean(false));
      } catch (IOException | RuntimeException unavailable) {
        retryAt = Instant.now().plusSeconds(60);
        destroy();
      }
    }
    return fallback(query);
  }

  private Result fallback(Query query) {
    return query.page() == 1 ? registrySearch(query) : Result.unavailable();
  }

  private Result registrySearch(Query query) {
    if (registry.isEmpty()) return Result.unavailable();
    try {
      if (snapshot == null) {
        try (var stream = Files.newInputStream(Path.of(registry))) {
          byte[] body = stream.readNBytes(8_000_001);
          if (body.length > 8_000_000) return Result.unavailable();
          snapshot = mapper.readerForListOf(Hotel.class).<List<Hotel>>readValue(body).stream()
              .filter(TripHotelCatalog::usable).toList();
        }
      }
      String city = key(query.city());
      return new Result(true, Trip.HotelProvider.RUSSIAN_TOURISM, snapshot.stream()
          .filter(hotel -> hotel.city() != null && key(hotel.city()).equals(city)).limit(50)
          .map(hotel -> new Hotel(hotel.id(), hotel.city(), hotel.name(), hotel.address(), hotel.latitude(),
              hotel.longitude(), hotel.stars(), hotel.rating(), hotel.photoUrl(), null, null, hotel.sourceUrl())).toList());
    } catch (IOException | RuntimeException unavailable) {
      return Result.unavailable();
    }
  }

  private static String key(String value) { return value.strip().toLowerCase(Locale.ROOT).replace('ё', 'е'); }

  private JsonNode hotelResponse(McpSchema.CallToolResult result) throws IOException {
    JsonNode structured = mapper.valueToTree(result.structuredContent());
    if (structured != null && structured.path("hotels").isArray()) return structured;
    for (var item : result.content() == null ? List.<McpSchema.Content>of() : result.content()) {
      if (item instanceof McpSchema.TextContent text && text.text().length() <= 1_000_000) {
        try {
          var parsed = mapper.readTree(text.text());
          if (parsed != null && parsed.path("hotels").isArray()) return parsed;
        } catch (IOException ignored) { }
      }
    }
    throw new IOException("Invalid hotel tool response");
  }

  private static boolean usable(Hotel hotel) {
    if (hotel == null || hotel.id() == null || hotel.id().isBlank() || hotel.id().length() > 160
        || hotel.name() == null || hotel.name().isBlank() || hotel.name().length() > 300) return false;
    if (hotel.latitude() != null && !(Math.abs(hotel.latitude()) <= 85)) return false;
    if (hotel.longitude() != null && !(Math.abs(hotel.longitude()) <= 180)) return false;
    var cost = hotel.cost();
    return cost == null || (cost.currency() != null && cost.currency().matches("[A-Z]{3}")
        && cost.amount() != null && cost.amount().signum() >= 0);
  }

  @Override
  public synchronized void destroy() {
    if (client == null) return;
    try { client.close(); } catch (RuntimeException ignored) { }
    finally { client = null; }
  }
}
