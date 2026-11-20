package ru.ticket.journey.domain.model;

import java.util.List;

/** Geographic selection sent by the map. Coordinates are in WGS84 decimal degrees. */
public record MapSelection(Shape type, List<Point> coordinates, Double radiusMeters) {
  /** Supported area tools. Rectangle uses two opposite corners. */
  public enum Shape { POLYGON, RECTANGLE, CIRCLE }
  /** Uses nullable fields to detect omitted coordinates at the boundary. */
  public record Point(Double latitude, Double longitude) {}
}
