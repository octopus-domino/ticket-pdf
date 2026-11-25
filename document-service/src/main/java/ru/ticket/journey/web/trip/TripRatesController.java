package ru.ticket.journey.web.trip;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ticket.common.ApiException;
import ru.ticket.journey.infrastructure.rates.BankOfRussiaRates;

/** Gives the budget editor dated exchange rates without receiving trip data. */
@RestController
@ConditionalOnProperty(name = "app.journeys.trip-planner-enabled", havingValue = "true")
public class TripRatesController {
  private final BankOfRussiaRates rates;

  /** Wires the official daily rate adapter. */
  public TripRatesController(BankOfRussiaRates rates) {
    this.rates = rates;
  }

  /** Returns the most recent available dated snapshot. */
  @GetMapping("/api/journeys/routes/places/rates")
  public BankOfRussiaRates.Snapshot rates() {
    var result = rates.current();
    if (result == null) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "journey.trip.rates-unavailable");
    return result;
  }
}
