package ru.ticket.walks.web.map;

import ru.ticket.walks.application.model.WalkPlace;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ticket.walks.shared.ApiException;
import ru.ticket.walks.domain.model.MapSelection;
import ru.ticket.walks.domain.model.MapSelection.Point;
import ru.ticket.walks.infrastructure.poi.SelectionGeometry;
import ru.ticket.walks.infrastructure.poi.WalkAreaSearchService;

/** Finds candidate places within an explicitly selected area. */
@RestController
@RequestMapping("/api/walks/v1/places/map")
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
  public List<WalkPlace> search(@RequestBody Query query) {
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
