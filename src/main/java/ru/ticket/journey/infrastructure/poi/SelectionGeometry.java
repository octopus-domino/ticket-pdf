package ru.ticket.journey.infrastructure.poi;

import java.util.Arrays;
import java.util.function.Predicate;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.springframework.stereotype.Component;
import ru.ticket.common.ApiException;
import ru.ticket.journey.domain.model.MapSelection;
import ru.ticket.journey.domain.model.MapSelection.Point;

/** Shapes the existing circular discovery request and filters results to the exact selection. */
@Component
public class SelectionGeometry {
  private static final GeometryFactory GEOMETRY = new GeometryFactory();
  private static final double MAX_RADIUS_METERS = 10_000;
  private static final double EARTH_RADIUS_METERS = 6_371_008.8;

  /** Lookup envelope and predicate for the final geographic area. */
  public record Area(Point center, double radiusKm, Predicate<Point> covers) {}

  /** Validates and prepares an area before any provider is contacted. */
  public Area prepare(MapSelection selection) {
    if (selection == null || selection.type() == null || selection.coordinates() == null
        || selection.coordinates().isEmpty() || selection.coordinates().size() > 256) throw invalid();
    var coordinates = selection.coordinates().stream().map(SelectionGeometry::coordinate).toArray(Coordinate[]::new);
    return switch (selection.type()) {
      case CIRCLE -> circle(selection, coordinates);
      case RECTANGLE -> shaped(rectangle(coordinates));
      case POLYGON -> shaped(polygon(coordinates));
    };
  }

  private static Area circle(MapSelection selection, Coordinate[] points) {
    Double radius = selection.radiusMeters();
    if (points.length != 1 || radius == null || !Double.isFinite(radius)
        || radius <= 0 || radius > MAX_RADIUS_METERS) throw invalid();
    var center = point(points[0]);
    return new Area(center, Math.max(100, radius) / 1000,
        candidate -> distance(center, candidate) <= radius);
  }

  private static org.locationtech.jts.geom.Geometry rectangle(Coordinate[] points) {
    if (points.length != 2) throw invalid();
    return GEOMETRY.toGeometry(new Envelope(points[0], points[1]));
  }

  private static org.locationtech.jts.geom.Geometry polygon(Coordinate[] points) {
    if (points.length < 3) throw invalid();
    if (!points[0].equals2D(points[points.length - 1])) {
      points = Arrays.copyOf(points, points.length + 1);
      points[points.length - 1] = points[0];
    }
    if (points.length < 4) throw invalid();
    return GEOMETRY.createPolygon(points);
  }

  private static Area shaped(org.locationtech.jts.geom.Geometry shape) {
    if (shape.getDimension() != 2 || !shape.isValid() || shape.getArea() == 0) throw invalid();
    var box = shape.getEnvelopeInternal();
    var center = point(box.centre());
    double radius = Math.max(distance(center, new Point(box.getMinY(), box.getMinX())),
        distance(center, new Point(box.getMaxY(), box.getMaxX())));
    if (radius > MAX_RADIUS_METERS || box.getWidth() > 180) throw invalid();
    var prepared = PreparedGeometryFactory.prepare(shape);
    return new Area(center, Math.max(100, radius) / 1000,
        candidate -> prepared.covers(GEOMETRY.createPoint(coordinate(candidate))));
  }

  private static Coordinate coordinate(Point point) {
    if (point == null || point.latitude() == null || point.longitude() == null
        || !Double.isFinite(point.latitude()) || !Double.isFinite(point.longitude())
        || Math.abs(point.latitude()) > 85 || Math.abs(point.longitude()) >= 180) throw invalid();
    return new Coordinate(point.longitude(), point.latitude());
  }

  private static Point point(Coordinate coordinate) {
    return new Point(coordinate.y, coordinate.x);
  }

  private static double distance(Point start, Point end) {
    double lat = Math.toRadians(end.latitude() - start.latitude());
    double lng = Math.toRadians(end.longitude() - start.longitude());
    double h = Math.pow(Math.sin(lat / 2), 2) + Math.cos(Math.toRadians(start.latitude()))
        * Math.cos(Math.toRadians(end.latitude())) * Math.pow(Math.sin(lng / 2), 2);
    return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(Math.min(1, h)));
  }

  private static ApiException invalid() {
    return ApiException.badRequest("journey.trip.invalid-map-selection");
  }
}
