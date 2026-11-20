package ru.ticket.journey.web.trip;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.ticket.journey.domain.service.CitySearch;

/** Provides explicit city lookup to the trip planner. */
@RestController
@ConditionalOnProperty(name = "app.journeys.trip-planner-enabled", havingValue = "true")
public class TripCitySearchController {
  private final CitySearch cities;

  /** Uses the configured city search adapter. */
  public TripCitySearchController(CitySearch cities) {
    this.cities = cities;
  }

  /** Returns settlements matching the submitted query. */
  @GetMapping("/api/journeys/routes/places/cities")
  public List<CitySearch.City> search(@RequestParam String q, @RequestParam(defaultValue = "ru") String language) {
    return cities.search(q, language);
  }
}
