package ru.ticket.routing.domain;

import static ru.ticket.routing.domain.RoutingFailure.Reason.*;

public record RoadRequest(Point start, Point end, Mode mode) {
    public enum Mode { CAR, FOOT }

    public record Point(double longitude, double latitude) {
        public Point {
            if (!Double.isFinite(longitude) || Math.abs(longitude) >= 180
                    || !Double.isFinite(latitude) || Math.abs(latitude) > 85)
                throw new RoutingFailure(INVALID_ROUTE);
        }

        public double distanceTo(Point other) {
            double lat = Math.toRadians(other.latitude - latitude);
            double lon = Math.toRadians(other.longitude - longitude);
            double h = Math.pow(Math.sin(lat / 2), 2)
                    + Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(other.latitude))
                    * Math.pow(Math.sin(lon / 2), 2);
            return 12_742_000 * Math.asin(Math.min(1, Math.sqrt(h)));
        }
    }

    public RoadRequest {
        if (start == null || end == null || mode == null) throw new RoutingFailure(INVALID_ROUTE);
        if (start.distanceTo(end) > (mode == Mode.CAR ? 1_500_000 : 400_000))
            throw new RoutingFailure(mode == Mode.CAR ? CAR_TOO_LONG : WALK_TOO_LONG);
    }
}
