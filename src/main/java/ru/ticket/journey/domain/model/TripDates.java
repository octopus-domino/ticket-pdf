package ru.ticket.journey.domain.model;

import java.time.LocalDate;

/** Inclusive calendar dates of a multi-day journey. */
public record TripDates(LocalDate startDate, LocalDate endDate) {
  /**
   * Validates the minimum trip length of two calendar days.
   *
   * @throws IllegalArgumentException if either date is absent or the range is too short
   */
  public TripDates {
    if (startDate == null || endDate == null || !endDate.isAfter(startDate)) {
      throw new IllegalArgumentException("journey.trip.minimum-two-days");
    }
  }
}
