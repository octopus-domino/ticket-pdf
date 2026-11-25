package ru.ticket.journey.web.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.ticket.auth.security.UserIdPrincipal;
import ru.ticket.common.ApiException;
import ru.ticket.journey.application.trip.LegacyTrips;
import ru.ticket.journey.application.journey.JourneyPrintService;
import ru.ticket.journey.domain.model.Trip;
import ru.ticket.journey.infrastructure.RoutingTripClient;

class TripDocumentControllerTest {
  private final LegacyTrips legacy = mock(LegacyTrips.class);
  private final RoutingTripClient trips = mock(RoutingTripClient.class);
  private final TripDocumentController controller =
      new TripDocumentController(legacy, trips, mock(JourneyPrintService.class));
  private final UserIdPrincipal owner = new UserIdPrincipal("owner");

  @Test
  void takesTheOwnerFromThePrincipalAndMakesResponsesPrivate() {
    var trip = trip();
    when(legacy.openLegacy("owner", "trip")).thenReturn(trip);
    var response = controller.legacy(owner, "trip");
    assertThat(response.getBody()).isEqualTo(trip);
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    verify(legacy).openLegacy("owner", "trip");
  }

  @Test
  void exportsTheAccountCalendarWithAnExclusiveEndDate() throws Exception {
    when(trips.read("trip", "Bearer owner-token")).thenReturn(trip());
    var response = controller.calendar("Bearer owner-token", "trip");
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    assertThat(response.getBody()).contains("BEGIN:VCALENDAR", "VERSION:2.0",
        "DTSTART;VALUE=DATE:20261231", "DTEND;VALUE=DATE:20270102", "SUMMARY:Travel");
  }

  @Test
  void rejectsAnonymousLegacyAccessBeforeReadingPublication() {
    assertThatThrownBy(() -> controller.legacy(null, "trip")).isInstanceOf(ApiException.class);
    verifyNoInteractions(legacy);
  }

  private static Trip trip() {
    var date = LocalDate.of(2026, 12, 31);
    return new Trip(2, "trip", "Travel", "", date, date.plusDays(1), "RUB", List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of(), null,
        new Trip.Meta(Trip.Mode.TRIP, Trip.Source.MANUAL, null, null, null, Map.of()));
  }
}
