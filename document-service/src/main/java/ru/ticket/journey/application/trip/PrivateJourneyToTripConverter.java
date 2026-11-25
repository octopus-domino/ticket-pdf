package ru.ticket.journey.application.trip;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.ticket.common.ApiException;
import ru.ticket.journey.application.journey.PrivateJourneyReader;
import ru.ticket.journey.domain.model.Trip;

/** Creates independent v2 travel drafts from a selected walking itinerary. */
@Component
public class PrivateJourneyToTripConverter {
  private final PrivateJourneyReader reader;

  /** Wires the SharedWalk boundary validator. */
  public PrivateJourneyToTripConverter(PrivateJourneyReader reader) {
    this.reader = reader;
  }

  /** Converts the selected walk with the user's date and optional destination label. */
  public Trip convert(PrivateJourneyReader.Transfer transfer, LocalDate date, String city) {
    if (date == null || date.equals(LocalDate.MAX)) throw ApiException.badRequest("journey.trip.choose-start-date");
    if (city != null && city.strip().length() > 160) throw ApiException.badRequest("journey.trip.invalid-city");
    var walk = reader.read(transfer);
    var segments = segments(walk.points());
    var first = walk.points().get(0);
    var destination = new Trip.City("city-1", city == null ? "" : city.strip(), null,
        first.lat(), first.lng(), null, null);
    var day = new Trip.Day(date, destination.id(), segments, null, null, sections(walk.sections(), segments));
    return new Trip(2, UUID.randomUUID().toString(), walk.title(), "", date, date.plusDays(1),
        "RUB", List.of(), List.of(destination), List.of(day), List.of(), List.of(), List.of(), null,
        new Trip.Meta(Trip.Mode.TRIP, Trip.Source.PRIVATE_JOURNEY, null, null, null, Map.of()));
  }

  private static List<Trip.Segment> segments(List<PrivateJourneyReader.Point> points) {
    var result = new ArrayList<Trip.Segment>();
    for (var point : points) {
      int number = result.size() + 1;
      String title = point.name() == null || point.name().isBlank() ? Integer.toString(number) : point.name().strip();
      result.add(new Trip.Segment("stop-" + number, title, point.lat(), point.lng(), null,
          null, null, Trip.Category.WALK, null, note(point), List.of(), false));
    }
    return result;
  }

  private static String note(PrivateJourneyReader.Point point) {
    if (point.sourceUrl() == null) return point.description();
    return point.description() == null || point.description().isBlank() ? point.sourceUrl()
        : point.description() + "\n" + point.sourceUrl();
  }

  private static List<Trip.WalkingSection> sections(List<PrivateJourneyReader.Section> input,
      List<Trip.Segment> segments) {
    var sections = new ArrayList<Trip.WalkingSection>();
    for (var section : input) {
      int index = sections.size();
      sections.add(new Trip.WalkingSection(segments.get(index).id(), segments.get(index + 1).id(),
          section.geometry(), section.distanceMeters(), section.durationSeconds()));
    }
    return sections;
  }
}
