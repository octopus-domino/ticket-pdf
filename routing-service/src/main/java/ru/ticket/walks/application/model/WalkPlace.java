package ru.ticket.walks.application.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** A place returned by the walking search. Coordinates retain their existing wire order. */
public record WalkPlace(
        String id, String label, double longitude, double latitude, String url, JsonNode schedule,
        String openingHours, String photoReference, String visitStatus, Integer wikidataSitelinks,
        Boolean inOsm, String source, String wikimapiaId, String description,
        Map<String, String> meta, Double score) {
  public WalkPlace(String id, String label, double longitude, double latitude, String url,
               JsonNode schedule, String openingHours, String photoReference) {
    this(id, label, longitude, latitude, url, schedule, openingHours, photoReference,
            null, null, null, null, null, null, null, null);
  }
  public WalkPlace(String id, String label, double longitude, double latitude, String url,
               JsonNode schedule, String openingHours) {
    this(id, label, longitude, latitude, url, schedule, openingHours, null);
  }
}
