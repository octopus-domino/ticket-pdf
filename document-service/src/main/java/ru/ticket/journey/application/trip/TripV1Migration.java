package ru.ticket.journey.application.trip;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import ru.ticket.journey.application.journey.RouteSnapshotService;
import ru.ticket.journey.application.journey.JourneyService.View;
import ru.ticket.journey.domain.model.Trip;

/** Projects a historical publication and its route into the v2 model without editing the original. */
public final class TripV1Migration {
  private TripV1Migration() {}

  /** Converts a historical route, retaining unknown fields and the original route identifier. */
  public static Trip convert(View publication, RouteSnapshotService.Snapshot route) {
    var fields = new LinkedHashMap<String, String>();
    if (publication.data() != null) publication.data().forEach((key, value) -> {
      if (key != null && value != null) fields.put(key, value);
    });
    var date = date(route == null ? null : route.startLocal(), fields.get("date"));
    var cities = new LinkedHashMap<String, Trip.City>();
    var visits = new ArrayList<Visit>();
    var transfers = new ArrayList<Trip.Leg>();
    var photoRefs = new LinkedHashMap<String, List<String>>();
    if (route != null && route.photos() != null) {
      for (var photo : route.photos()) {
        if (photo != null && photo.stopId() != null && photo.imageId() != null && !photo.imageId().isBlank()) {
          photoRefs.computeIfAbsent(photo.stopId(), ignored -> new ArrayList<>()).add(photo.imageId());
        }
      }
    }
    if (route != null && route.stops() != null) {
      for (int index = 0; index < route.stops().size(); index++) {
        var stop = route.stops().get(index);
        if (stop == null) continue;
        var previous = visits.isEmpty() ? null : visits.get(visits.size() - 1);
        addStop(stop, date, fields.get("city"), cities, visits, photoRefs.getOrDefault(stop.id(), List.of()));
        if (previous == null) continue;
        var current = visits.get(visits.size() - 1);
        var before = index > 0 ? route.stops().get(index - 1) : null;
        var legacy = before != null && route.legs() != null && index - 1 < route.legs().size()
            ? route.legs().get(index - 1) : null;
        var geometry = geometry(legacy);
        if (previous != current) {
          transfers.add(new Trip.Leg("leg-" + (transfers.size() + 1), previous.cityId(), current.cityId(), transport(legacy),
              legacy == null ? null : time(legacy.departure(), before.zone()),
              legacy == null ? null : time(legacy.arrival(), stop.zone()), null, null, null, null,
              geometry, legacy == null ? null : nonNegative(legacy.distanceMeters()),
              legacy == null ? null : nonNegative(legacy.durationSeconds())));
        } else if (before != null && legacy != null && "foot".equals(legacy.mode()) && geometry.size() > 1
            && before.id() != null && stop.id() != null) {
          current.walking().add(new Trip.WalkingSection(before.id(), stop.id(), geometry,
              Objects.requireNonNullElse(nonNegative(legacy.distanceMeters()), 0d),
              Objects.requireNonNullElse(nonNegative(legacy.durationSeconds()), 0d)));
        }
      }
    }
    return new Trip(2, publication.id(), publication.title(), fields.getOrDefault("description", ""),
        date, date, currency(fields.get("currency")), List.of(), List.copyOf(cities.values()),
        visits.stream().map(Visit::day).toList(), transfers, List.of(), List.of(), null,
        new Trip.Meta(Trip.Mode.WALK, Trip.Source.MANUAL, fields.get("organizer"),
            fields.get("contact"), fields.get("route"), fields));
  }

  private static void addStop(RouteSnapshotService.Stop stop, LocalDate date, String defaultCity,
      Map<String, Trip.City> cities, List<Visit> visits, List<String> photoRefs) {
    var metadata = stop.meta() == null ? Map.<String, String>of() : stop.meta();
    String cityName = Objects.requireNonNullElse(metadata.get("city"), Objects.requireNonNullElse(defaultCity, "")).strip();
    String timezone = zone(stop.zone());
    String cityKey = cityName.isEmpty() ? "" : cityName + '\u001f' + timezone;
    var city = cities.computeIfAbsent(cityKey, ignored -> new Trip.City("city-" + (cities.size() + 1),
        cityName, metadata.get("country"), latitude(stop.latitude()), longitude(stop.longitude()), timezone,
        metadata.get("geonameId")));
    if (visits.isEmpty() || !visits.get(visits.size() - 1).cityId().equals(city.id())) {
      visits.add(new Visit(date, city.id(), new ArrayList<>(), new ArrayList<>()));
    }
    var label = Objects.requireNonNullElse(stop.label(), "");
    visits.get(visits.size() - 1).segments().add(new Trip.Segment(stop.id(), label, latitude(stop.latitude()),
        longitude(stop.longitude()), metadata.get("address"), null, null, Trip.Category.WALK,
        null, stop.description(), photoRefs, false));
  }

  private static List<Trip.Coordinate> geometry(RouteSnapshotService.Leg leg) {
    if (leg == null || leg.coordinates() == null) return List.of();
    var result = new ArrayList<Trip.Coordinate>();
    for (var tuple : leg.coordinates()) {
      if (tuple == null || tuple.size() != 2 || tuple.get(0) == null || tuple.get(1) == null) return List.of();
      Double lon = longitude(tuple.get(0)), lat = latitude(tuple.get(1));
      if (lon == null || lat == null) return List.of();
      result.add(new Trip.Coordinate(lat, lon));
    }
    return List.copyOf(result);
  }

  private static Trip.Transport transport(RouteSnapshotService.Leg leg) {
    if (leg == null) return Trip.Transport.WALK;
    if ("flight".equals(leg.mode())) return Trip.Transport.PLANE;
    return "car".equals(leg.mode()) ? Trip.Transport.CAR : Trip.Transport.WALK;
  }

  private static Double nonNegative(Double value) {
    return value != null && Double.isFinite(value) && value >= 0 ? value : null;
  }

  private static ZonedDateTime time(String value, String zone) {
    if (value == null) return null;
    try { return ZonedDateTime.parse(value); }
    catch (DateTimeException invalid) {
      try { return zone == null ? null : LocalDateTime.parse(value).atZone(ZoneId.of(zone)); }
      catch (DateTimeException unknown) { return null; }
    }
  }

  private static LocalDate date(String startLocal, String fallback) {
    try {
      if (startLocal != null) return LocalDateTime.parse(startLocal).toLocalDate();
    } catch (DateTimeException invalid) {
      // Legacy route times can be incomplete. Try the publication date.
    }
    try {
      return fallback == null ? null : LocalDate.parse(fallback);
    } catch (DateTimeException invalid) {
      return null;
    }
  }

  private static String zone(String value) {
    if (value == null) return null;
    try {
      return ZoneId.of(value).getId();
    } catch (DateTimeException invalid) {
      return null;
    }
  }

  private static String currency(String value) {
    return value != null && value.matches("[A-Z]{3}") ? value : "RUB";
  }

  private static Double latitude(double value) {
    return Math.abs(value) <= 85 ? value : null;
  }

  private static Double longitude(double value) {
    return Math.abs(value) < 180 ? value : null;
  }

  private record Visit(LocalDate date, String cityId, List<Trip.Segment> segments, List<Trip.WalkingSection> walking) {
    Trip.Day day() {
      return new Trip.Day(date, cityId, segments, null, null, walking);
    }
  }
}
