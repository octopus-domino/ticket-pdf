package ru.ticket.journey.web.trip;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.ticket.common.ApiException;
import ru.ticket.journey.domain.service.HotelProvider;

/** Searches accommodation by city, dates and party size without receiving the trip. */
@RestController
@ConditionalOnProperty(name = "app.journeys.trip-planner-enabled", havingValue = "true")
public class TripHotelController {
  private final HotelProvider hotels;

  public TripHotelController(HotelProvider hotels) {
    this.hotels = hotels;
  }

  /** Returns the provider status and available hotel candidates. */
  @PostMapping("/api/journeys/routes/places/hotels")
  public HotelProvider.Result search(@RequestBody HotelProvider.Query query) {
    if (query == null) throw ApiException.badRequest("journey.trip.invalid-hotel-query");
    return hotels.search(query);
  }
}
