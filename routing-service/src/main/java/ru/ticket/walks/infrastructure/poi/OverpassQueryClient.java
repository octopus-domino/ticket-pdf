package ru.ticket.walks.infrastructure.poi;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Failover strategy: one OSM query can use another public instance within its existing time budget. */
@Component
public class OverpassQueryClient {
  private static final int MAX_ENDPOINTS = 3;
  private final WalkRemote remote;
  private final List<String> endpoints;

  @Autowired
  public OverpassQueryClient(WalkRemote remote,
      @Value("${app.journeys.overpass-url:https://overpass-api.de/api/interpreter}") String primary,
      @Value("${app.journeys.overpass-fallback-urls:https://overpass.private.coffee/api/interpreter,https://maps.mail.ru/osm/tools/overpass/api/interpreter}") String fallback) {
    this(remote, Stream.concat(Stream.of(primary), Arrays.stream(fallback.split(",")))
        .map(String::trim).filter(value -> !value.isEmpty()).distinct().limit(MAX_ENDPOINTS).toList());
  }

  /** Allows deterministic transports and endpoint lists in tests and isolated deployments. */
  public OverpassQueryClient(WalkRemote remote, List<String> endpoints) {
    this.remote = remote;
    this.endpoints = List.copyOf(endpoints);
  }

  /** Never changes dataset, language, filters or result count when a server is unavailable. */
  public JsonNode query(String query) throws IOException {
    IOException failure = new IOException("No OSM query endpoint configured");
    for (String endpoint : endpoints) {
      if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("OSM query cancelled");
      try {
        JsonNode result = remote.post(endpoint, "data=" + WalkRemote.encode(query));
        if (!result.path("elements").isArray() || result.has("remark")) throw new IOException("Incomplete OSM response");
        return result;
      } catch (WalkRemote.ResponseTooLarge oversized) {
        throw oversized;
      } catch (IOException unavailable) {
        failure = unavailable;
      }
    }
    throw new IOException("OSM query instances unavailable", failure);
  }
}
