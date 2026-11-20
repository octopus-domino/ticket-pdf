package ru.ticket.journey.web.map;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ticket.common.ApiException;
import ru.ticket.journey.PlaceRecommendationsController.Place;
import ru.ticket.journey.domain.model.MapSelection;
import ru.ticket.journey.domain.model.MapSelection.Point;
import ru.ticket.journey.infrastructure.poi.SelectionGeometry;
import ru.ticket.journey.infrastructure.poi.WalkAreaSearchService;

/** Finds candidate places within an explicitly selected area. */
@RestController
@RequestMapping("/api/journeys/routes/places/map")
@ConditionalOnProperty(name = "app.journeys.trip-planner-enabled", havingValue = "true")
public class MapSelectionController {
  public record Query(MapSelection selection, List<String> themes, String language) {}

  private final SelectionGeometry geometry;
  private final WalkAreaSearchService places;

  public MapSelectionController(SelectionGeometry geometry, WalkAreaSearchService places) {
    this.geometry = geometry;
    this.places = places;
  }

  @PostMapping("/selection")
  public List<Place> search(@RequestBody Query query) {
    if (query == null || query.themes() == null || query.themes().isEmpty())
      throw ApiException.badRequest("journey.trip.choose-place-themes");
    var area = geometry.prepare(query.selection());
    var center = area.center();
    var request = new WalkAreaSearchService.Request(center.longitude(), center.latitude(),
        query.themes(), query.language() == null ? "ru" : query.language(), area.radiusKm(), null);
    return places.search(request).stream()
        .filter(place -> area.covers().test(new Point(place.latitude(), place.longitude())))
        .toList();
  }
}
