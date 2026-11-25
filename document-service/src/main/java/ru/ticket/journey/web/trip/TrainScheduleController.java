package ru.ticket.journey.web.trip;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.ticket.common.ApiException;
import ru.ticket.journey.domain.service.TrainScheduleProvider;

/** Provides timetable searches to guest and authenticated trip planners. */
@RestController
@RequestMapping("/api/journeys/routes/places/trains")
@ConditionalOnProperty(name = "app.journeys.trip-planner-enabled", havingValue = "true")
public class TrainScheduleController {
  private final TrainScheduleProvider schedules;

  public TrainScheduleController(TrainScheduleProvider schedules) {
    this.schedules = schedules;
  }

  @PostMapping
  public TrainScheduleProvider.Result search(@RequestBody TrainScheduleProvider.Query query) {
    if (query == null) throw ApiException.badRequest("journey.trip.invalid-train-query");
    return schedules.search(query);
  }

  /** Finds railway codes by city or station name. */
  @GetMapping("/places")
  public List<TrainScheduleProvider.Place> places(@RequestParam String q) {
    return schedules.places(q);
  }
}
