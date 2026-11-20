package ru.ticket.journey.domain.service;

import java.util.List;

/** Routing engine boundary; map rendering and journey planning belong to the client. */
public interface RoadRouter {
  /** Longitude and latitude in decimal degrees. */
  record Point(double longitude, double latitude) {}

  /** Provider geometry in longitude–latitude order with distance and duration. */
  record Road(List<List<Double>> coordinates, double distanceMeters, double durationSeconds) {}

  /** Calculates a road between the supplied points for the given transport mode. */
  Road route(Point start, Point end, String mode);
}
