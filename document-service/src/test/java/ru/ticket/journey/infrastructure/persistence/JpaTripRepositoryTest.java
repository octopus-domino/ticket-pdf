package ru.ticket.journey.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import ru.ticket.journey.domain.model.Trip;
import ru.ticket.journey.domain.repository.TripRepository;

@DataJpaTest(properties = "app.journeys.trip-planner-enabled=true")
@Import(JpaTripRepository.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
class JpaTripRepositoryTest {
  @Autowired TripRepository trips;
  @Autowired EntityManager entities;

  @Test
  void storesAndUpdatesTheSameIdentifierIndependentlyForEachOwner() {
    trips.saveOwned("owner-a", trip("First"));
    trips.saveOwned("owner-b", trip("Second"));
    entities.flush();
    entities.clear();
    assertThat(trips.findOwned("owner-a", "trip")).contains(trip("First"));
    assertThat(trips.findOwned("owner-b", "trip")).contains(trip("Second"));
    assertThat(trips.findOwned("owner-c", "trip")).isEmpty();
    trips.saveOwned("owner-a", trip("Updated"));
    entities.flush();
    entities.clear();
    assertThat(trips.findOwned("owner-a", "trip")).contains(trip("Updated"));
    assertThat(trips.findOwned("owner-b", "trip")).contains(trip("Second"));
  }

  private static Trip trip(String title) {
    var start = LocalDate.of(2026, 12, 31);
    return new Trip(2, "trip", title, "", start, start.plusDays(1), "RUB", List.of(),
        List.of(), List.of(), List.of(), List.of(), List.of(), null,
        new Trip.Meta(Trip.Mode.TRIP, Trip.Source.MANUAL, null, null, null, Map.of()));
  }
}
