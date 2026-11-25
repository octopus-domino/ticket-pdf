package ru.ticket.journey.domain.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TripDatesTest {
  @Test
  void requiresTwoCalendarDays() {
    LocalDate start = LocalDate.of(2026, 12, 31);
    assertDoesNotThrow(() -> new TripDates(start, start.plusDays(1)));
    assertThrows(IllegalArgumentException.class, () -> new TripDates(start, start));
    assertThrows(IllegalArgumentException.class, () -> new TripDates(start, start.minusDays(1)));
    assertThrows(IllegalArgumentException.class, () -> new TripDates(start, null));
  }
}
