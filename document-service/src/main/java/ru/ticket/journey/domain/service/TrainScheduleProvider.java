package ru.ticket.journey.domain.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Provides scheduled railway services between known stations or cities. */
public interface TrainScheduleProvider {
  /** Search for the local date at the departure station. */
  record Query(String from, String to, LocalDate date) {
    /** Validates Yandex settlement or station codes. */
    public Query {
      if (from == null || to == null || !from.matches("[cs][0-9]{1,12}")
          || !to.matches("[cs][0-9]{1,12}") || from.equals(to) || date == null) {
        throw new IllegalArgumentException("journey.trip.invalid-train-query");
      }
    }
  }

  /** Time and station information reported by the operator. */
  record Train(String number, String title, String from, String to, OffsetDateTime departure, OffsetDateTime arrival) {}

  /** Distinguishes a cached fallback from a fresh response and an unavailable provider. */
  record Result(List<Train> trains, boolean stale, boolean available) {}

  /** A railway settlement or station with its code and human-readable context. */
  record Place(String code, String title, String region) {}

  /** Returns direct rail services for a station or city pair. */
  Result search(Query query);

  /** Finds settlement names and station names in the provider directory. */
  List<Place> places(String text);
}
