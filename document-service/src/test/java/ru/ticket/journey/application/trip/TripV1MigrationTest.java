package ru.ticket.journey.application.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.ticket.journey.application.journey.RouteSnapshotService.Snapshot;
import ru.ticket.journey.application.journey.RouteSnapshotService.Stop;
import ru.ticket.journey.application.journey.RouteSnapshotService.Leg;
import ru.ticket.journey.domain.model.RoutePhoto;
import ru.ticket.journey.application.journey.JourneyService.Kind;
import ru.ticket.journey.application.journey.JourneyService.View;
import ru.ticket.journey.domain.model.Trip;

class TripV1MigrationTest {
  @Test
  void handlesEmptyAndDamagedLegacyData() {
    var trip = TripV1Migration.convert(publication(Map.of("date", "bad", "currency", "?")), null);
    assertThat(trip.startDate()).isNull();
    assertThat(trip.days()).isEmpty();
    assertThat(trip.baseCurrency()).isEqualTo("RUB");
    assertThat(trip.meta().legacyFields()).containsEntry("date", "bad");

    var brokenStop = new Stop("stop", null, Double.NaN, 95, "invalid-zone", 0);
    var converted = TripV1Migration.convert(publication(Map.of("date", "2026-11-20")),
        snapshot("bad", List.of(brokenStop)));
    assertThat(converted.startDate()).isEqualTo(LocalDate.of(2026, 11, 20));
    var segment = converted.days().get(0).segments().get(0);
    assertThat(segment.longitude()).isNull();
    assertThat(segment.latitude()).isNull();
    assertThat(converted.cities().get(0).timezone()).isNull();
  }

  @Test
  void keepsSingleCityStopOrderAndLegacyFields() {
    var trip = TripV1Migration.convert(publication(Map.of("organizer", "Organizer", "contact", "Contact",
        "route", "snapshot", "city", "Kazan")), snapshot("2026-11-21T09:00",
        List.of(stop("a", ""), stop("b", ""), stop("c", ""), stop("d", ""), stop("e", ""))));
    assertThat(trip.version()).isEqualTo(2);
    assertThat(trip.cities()).extracting(Trip.City::name).containsExactly("Kazan");
    assertThat(trip.days().get(0).segments()).extracting(Trip.Segment::id).containsExactly("a", "b", "c", "d", "e");
    assertThat(trip.meta().legacyRouteId()).isEqualTo("snapshot");
    assertThat(trip.meta().organizer()).isEqualTo("Organizer");
    assertThat(trip.meta().contact()).isEqualTo("Contact");
    assertThat(trip.meta().mode()).isEqualTo(Trip.Mode.WALK);
  }

  @Test
  void createsAnOrderedVisitAndWalkingLegForEachCityChange() {
    var trip = TripV1Migration.convert(publication(Map.of()),
        snapshot("2026-11-21T09:00", List.of(stop("a", "Kazan"), stop("b", "Moscow"))));
    assertThat(trip.cities()).extracting(Trip.City::name).containsExactly("Kazan", "Moscow");
    assertThat(trip.days()).extracting(Trip.Day::cityId).containsExactly("city-1", "city-2");
    assertThat(trip.legs()).singleElement().satisfies(leg -> {
      assertThat(leg.fromCityId()).isEqualTo("city-1");
      assertThat(leg.toCityId()).isEqualTo("city-2");
      assertThat(leg.transport()).isEqualTo(Trip.Transport.WALK);
    });
  }

  @Test
  void retainsTheWalkingLineAndTheActualIntercityTransfer() {
    var first = new Leg("foot", 900, null, null, null, null,
        List.of(List.of(49.1, 55.8), List.of(49.2, 55.9)), 1500d);
    var second = new Leg("flight", 3600, "2026-11-21T09:00", "2026-11-21T10:00", null, null,
        List.of(List.of(49.2, 55.9), List.of(37.6, 55.7)), 719_000d);
    var snapshot = new Snapshot(List.of(stop("a", "Kazan"), stop("b", "Kazan"), stop("c", "Moscow")),
        List.of(first, second), "2026-11-21T08:00", List.of(), null,
        List.of(new RoutePhoto("a", "photo-a", "https://example.invalid", "Author", "CC BY", "https://creativecommons.org")));
    var trip = TripV1Migration.convert(publication(Map.of()), snapshot);
    assertThat(trip.days().get(0).segments().get(0).photoRefs()).containsExactly("photo-a");
    assertThat(trip.days().get(0).walkingSections()).singleElement().satisfies(section -> {
      assertThat(section.fromSegmentId()).isEqualTo("a");
      assertThat(section.toSegmentId()).isEqualTo("b");
      assertThat(section.geometry()).containsExactly(new Trip.Coordinate(55.8, 49.1), new Trip.Coordinate(55.9, 49.2));
    });
    assertThat(trip.legs()).singleElement().satisfies(leg -> {
      assertThat(leg.transport()).isEqualTo(Trip.Transport.PLANE);
      assertThat(leg.departureAt().getOffset().getTotalSeconds()).isEqualTo(10800);
      assertThat(leg.distanceMeters()).isEqualTo(719_000d);
      assertThat(leg.geometry()).hasSize(2);
    });
  }

  @Test
  void rejectsASingleDayInTravelMode() {
    var date = LocalDate.of(2026, 11, 20);
    assertThatThrownBy(() -> new Trip(2, "trip", "Title", "", date, date, "RUB",
        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null,
        new Trip.Meta(Trip.Mode.TRIP, Trip.Source.MANUAL, null, null, null, Map.of())))
        .hasMessage("journey.trip.minimum-two-days");
  }

  private static View publication(Map<String, String> fields) {
    return new View("legacy", Kind.TOUR, "Walk", fields, Instant.EPOCH);
  }

  private static Stop stop(String id, String city) {
    return new Stop(id, id, 49.1, 55.8, "Europe/Moscow", 0, "Notes", null,
        city.isEmpty() ? Map.of() : Map.of("city", city));
  }

  private static Snapshot snapshot(String startLocal, List<Stop> stops) {
    return new Snapshot(stops, List.of(), startLocal, List.of(), null);
  }
}
