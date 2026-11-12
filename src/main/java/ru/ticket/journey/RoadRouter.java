package ru.ticket.journey;

import java.util.List;

/** Routing engine boundary; map rendering and journey planning belong to the client. */
public interface RoadRouter {
  record Point(double longitude, double latitude) {}

  record Road(List<List<Double>> coordinates, double distanceMeters, double durationSeconds) {}

  Road route(Point start, Point end, String mode);
}
