package ru.ticket.journey.infrastructure.calendar;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.temporal.Temporal;
import java.util.Objects;
import java.util.UUID;
import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;
import ru.ticket.journey.domain.model.Trip;

/** Produces an iCalendar snapshot of a trip without participant contact data. */
public final class TripCalendar {
  private TripCalendar() {}

  /** Exports the trip, planned walks, dated transport and accommodation. */
  public static String render(Trip trip) throws IOException {
    var calendar = new Calendar();
    calendar.add(new ProdId("-//CoDoc//Trip planner//EN"));
    var version = new Version();
    version.setValue(Version.VALUE_2_0);
    calendar.add(version);
    if (trip.startDate() != null && trip.endDate() != null) {
      calendar.add(event(trip, "trip", trip.startDate(), trip.endDate().plusDays(1), trip.title()));
    }
    for (var day : trip.days()) {
      if (day.date() != null && day.note() != null && !day.note().isBlank()) {
        calendar.add(event(trip, "day-" + day.date(), day.date(), day.date().plusDays(1), day.note()));
      }
    }
    for (var leg : trip.legs()) {
      if (leg.departureAt() != null && leg.arrivalAt() != null && leg.arrivalAt().isAfter(leg.departureAt())) {
        calendar.add(event(trip, "leg-" + leg.id(), leg.departureAt().toInstant(), leg.arrivalAt().toInstant(),
            leg.note() == null && leg.transport() != null ? leg.transport().name() : leg.note()));
      }
    }
    for (var lodging : trip.accommodations()) {
      if (lodging.checkIn() != null && lodging.checkOut() != null && lodging.checkOut().isAfter(lodging.checkIn())) {
        calendar.add(event(trip, "lodging-" + lodging.id(), lodging.checkIn(), lodging.checkOut(), lodging.name()));
      }
    }
    var writer = new StringWriter();
    new CalendarOutputter().output(calendar, writer);
    return writer.toString();
  }

  private static VEvent event(Trip trip, String key, Temporal start, Temporal end, String title) {
    var event = new VEvent(start, end, Objects.requireNonNullElse(title, "Trip"));
    var id = UUID.nameUUIDFromBytes((trip.id() + "/" + key).getBytes(StandardCharsets.UTF_8));
    event.add(new Uid(id + "@codoc.site"));
    return event;
  }
}
