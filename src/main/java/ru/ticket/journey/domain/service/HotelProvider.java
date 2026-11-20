package ru.ticket.journey.domain.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import ru.ticket.journey.domain.model.Trip;

/** Finds accommodation candidates for a city and travel dates. */
public interface HotelProvider {
  /** Validated search criteria shared across accommodation sources. */
  record Query(String city, LocalDate checkIn, LocalDate checkOut, int guests) {
    public Query {
      city = city == null ? "" : city.strip();
      if (city.length() < 2 || city.length() > 120 || checkIn == null || checkOut == null
          || !checkOut.isAfter(checkIn) || ChronoUnit.DAYS.between(checkIn, checkOut) > 90
          || guests < 1 || guests > 12) throw new IllegalArgumentException("journey.trip.invalid-hotel-query");
    }
  }

  /** A search result, with optional live price and cancellation terms. */
  record Hotel(String id, String city, String name, String address, Double latitude, Double longitude,
      Integer stars, Double rating, String photoUrl, Trip.Money cost, Boolean freeCancellation, String sourceUrl) {}

  /** Results and the source that produced them. */
  record Result(boolean available, Trip.HotelProvider provider, List<Hotel> hotels, boolean catalogOnly) {
    public Result(boolean available, Trip.HotelProvider provider, List<Hotel> hotels) {
      this(available, provider, hotels, provider == Trip.HotelProvider.RUSSIAN_TOURISM);
    }
    public static Result unavailable() { return new Result(false, null, List.of()); }
  }

  /** Returns candidates or reports that the configured sources are unavailable. */
  Result search(Query query);
}
