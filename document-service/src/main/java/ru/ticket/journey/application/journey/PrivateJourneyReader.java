package ru.ticket.journey.application.journey;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import ru.ticket.common.ApiException;
import ru.ticket.journey.domain.model.Trip.Coordinate;

/** Reads the explicitly selected SharedWalk payload from the private Journey client. */
@Component
public class PrivateJourneyReader {
  /** Browser handoff. Source IDs and timestamps are informational and grant no access. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Transfer(int version, String title, List<Point> points, Route route) {}
  /** A stop from the private walk. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Point(Double lat, Double lng, String name, String description, String sourceUrl) {}
  /** Collection of consecutive edges from SharedWalk.route. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Route(List<Edge> legs) {}
  /** An original walking leg with latitude-longitude tuples. */
  public record Edge(Integer fromIndex, Integer toIndex, List<List<Double>> geometry, double distanceMeters,
      double durationSeconds) {}
  /** Validated walk with copied stop and section lists. */
  public record Walk(String title, List<Point> points, List<Section> sections) {}
  /** Validated local geometry and walking metrics. */
  public record Section(List<Coordinate> geometry, double distanceMeters, double durationSeconds) {}

  /** Checks completeness, ordering and bounded payload size before converting a walk. */
  public Walk read(Transfer transfer) {
    if (transfer == null || transfer.version() != 1 || transfer.title() == null
        || transfer.title().isBlank() || transfer.title().strip().length() > 200
        || transfer.points() == null || transfer.points().size() < 2 || transfer.points().size() > 200
        || transfer.route() == null || transfer.route().legs() == null
        || transfer.route().legs().size() != transfer.points().size() - 1) throw invalid();
    transfer.points().forEach(PrivateJourneyReader::validatePoint);
    var sections = new ArrayList<Section>();
    int remaining = 30_000;
    for (var edge : transfer.route().legs()) {
      validateEdge(edge, sections.size(), remaining);
      var coordinates = edge.geometry().stream().map(PrivateJourneyReader::coordinate).toList();
      remaining -= coordinates.size();
      sections.add(new Section(coordinates, edge.distanceMeters(), edge.durationSeconds()));
    }
    return new Walk(transfer.title().strip(), List.copyOf(transfer.points()), List.copyOf(sections));
  }

  private static void validatePoint(Point point) {
    if (point == null || point.lat() == null || point.lng() == null
        || length(point.name()) > 300 || length(point.description()) > 500
        || length(point.sourceUrl()) > 2048
        || point.sourceUrl() != null && !point.sourceUrl().startsWith("https://")) throw invalid();
    checked(point.lat(), point.lng());
  }

  private static void validateEdge(Edge edge, int index, int remaining) {
    if (edge == null || edge.fromIndex() == null || edge.toIndex() == null
        || edge.fromIndex() != index || edge.toIndex() != index + 1
        || edge.geometry() == null || edge.geometry().size() < 2 || edge.geometry().size() > remaining
        || !Double.isFinite(edge.distanceMeters()) || edge.distanceMeters() < 0
        || !Double.isFinite(edge.durationSeconds()) || edge.durationSeconds() <= 0
        || edge.durationSeconds() > 43_200) throw invalid();
  }

  private static Coordinate coordinate(List<Double> tuple) {
    if (tuple == null || tuple.size() != 2 || tuple.get(0) == null || tuple.get(1) == null) throw invalid();
    return checked(tuple.get(0), tuple.get(1));
  }

  private static Coordinate checked(double lat, double lng) {
    if (!Double.isFinite(lat) || !Double.isFinite(lng) || Math.abs(lat) > 85 || Math.abs(lng) > 180) throw invalid();
    return new Coordinate(lat, lng);
  }

  private static int length(String value) {
    return value == null ? 0 : value.length();
  }

  private static ApiException invalid() {
    return ApiException.badRequest("journey.trip.invalid-private-walk");
  }
}
