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
import ru.ticket.journey.application.trip.TripDraftService;
import ru.ticket.journey.application.journey.JourneyPrintService;
import ru.ticket.journey.domain.model.Trip;

class TripDraftControllerTest {
  private final TripDraftService drafts = mock(TripDraftService.class);
  private final TripDraftController controller = new TripDraftController(drafts, mock(JourneyPrintService.class));
  private final UserIdPrincipal owner = new UserIdPrincipal("owner");

  @Test
  void takesTheOwnerFromThePrincipalAndMakesResponsesPrivate() {
    var trip = trip();
    when(drafts.saveOwned("owner", trip)).thenReturn(trip);
    when(drafts.openOwned("owner", "trip")).thenReturn(trip);
    assertThat(controller.save(owner, "trip", trip).getBody()).isEqualTo(trip);
    assertThat(controller.open(owner, "trip").getHeaders().getCacheControl()).isEqualTo("no-store");
    verify(drafts).saveOwned("owner", trip);
    verify(drafts).openOwned("owner", "trip");
  }

  @Test
  void exportsTheAccountCalendarWithAnExclusiveEndDate() throws Exception {
    when(drafts.openOwned("owner", "trip")).thenReturn(trip());
    var response = controller.calendar(owner, "trip");
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    assertThat(response.getBody()).contains("BEGIN:VCALENDAR", "VERSION:2.0",
        "DTSTART;VALUE=DATE:20261231", "DTEND;VALUE=DATE:20270102", "SUMMARY:Travel");
  }

  @Test
  void rejectsAnonymousAccessAndMismatchedIdentifiersBeforeStorage() {
    assertThatThrownBy(() -> controller.open(null, "trip")).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> controller.save(owner, "other", trip())).isInstanceOf(ApiException.class);
    verifyNoInteractions(drafts);
  }

  private static Trip trip() {
    var date = LocalDate.of(2026, 12, 31);
    return new Trip(2, "trip", "Travel", "", date, date.plusDays(1), "RUB", List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of(), null,
        new Trip.Meta(Trip.Mode.TRIP, Trip.Source.MANUAL, null, null, null, Map.of()));
  }
}
