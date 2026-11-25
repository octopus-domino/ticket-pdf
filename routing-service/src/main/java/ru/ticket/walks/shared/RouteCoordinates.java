package ru.ticket.walks.shared;

import ru.ticket.walks.shared.ApiException;

/** Validates points at the existing route and place API boundaries. */
public final class RouteCoordinates {
  private RouteCoordinates() {}

  /** Rejects non-finite or out-of-range coordinates using the existing route error code. */
  public static void validate(double longitude, double latitude) {
    if (!Double.isFinite(longitude)
        || !Double.isFinite(latitude)
        || Math.abs(longitude) >= 180
        || Math.abs(latitude) > 85) {
      throw ApiException.badRequest("journey.route.invalid-route");
    }
  }
}
