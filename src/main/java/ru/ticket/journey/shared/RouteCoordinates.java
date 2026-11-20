package ru.ticket.journey.shared;

import ru.ticket.common.ApiException;
import ru.ticket.journey.domain.service.RoadRouter.Point;

/** Validates points at the existing route and place API boundaries. */
public final class RouteCoordinates {
  private RouteCoordinates() {}

  /** Rejects missing or out-of-range points using the existing route error code. */
  public static void validate(Point point) {
    if (point == null
        || !Double.isFinite(point.longitude())
        || !Double.isFinite(point.latitude())
        || Math.abs(point.longitude()) >= 180
        || Math.abs(point.latitude()) > 85) {
      throw ApiException.badRequest("journey.route.invalid-route");
    }
  }
}
