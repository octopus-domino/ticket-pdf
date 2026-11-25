package ru.ticket.journey.application.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import ru.ticket.journey.domain.model.Trip;
import ru.ticket.journey.domain.repository.TripRepository;

class TripDraftServiceTest {
  @Test
  void publishesTheChangeAfterTheRepositorySavesTheTrip() {
    var repository = mock(TripRepository.class);
    var events = mock(ApplicationEventPublisher.class);
    var trip = mock(Trip.class);
    when(trip.id()).thenReturn("trip-id");
    when(repository.saveOwned("owner", trip)).thenReturn(trip);
    var service = new TripDraftService(repository, null, null, null, events);
    assertThat(service.saveOwned("owner", trip)).isSameAs(trip);
    var ordered = inOrder(repository, events);
    ordered.verify(repository).saveOwned("owner", trip);
    ordered.verify(events).publishEvent(new TripChangedEvent("trip-id"));
  }
}
