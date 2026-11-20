package ru.ticket.journey.web.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.ticket.common.ApiException;
import ru.ticket.journey.PlaceRecommendationsController.Place;
import ru.ticket.journey.domain.model.MapSelection;
import ru.ticket.journey.domain.model.MapSelection.Point;
import ru.ticket.journey.infrastructure.poi.SelectionGeometry;
import ru.ticket.journey.infrastructure.poi.WalkAreaSearchService;

class MapSelectionControllerTest {
  private final WalkAreaSearchService places = mock(WalkAreaSearchService.class);
  private final MapSelectionController controller = new MapSelectionController(new SelectionGeometry(), places);

  @Test
  void returnsPlacesInsideAndOnTheBoundaryOfThePolygon() throws Exception {
    when(places.search(any())).thenReturn(List.of(place("inside", 1.002, 1.002),
        place("boundary", 1.010, 1.0), place("outside", 1.009, 1.009)));
    MockMvcBuilders.standaloneSetup(controller).build()
        .perform(post("/api/journeys/routes/places/map/selection")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"selection":{"type":"POLYGON","coordinates":[{"latitude":1.0,"longitude":1.0},
                  {"latitude":1.01,"longitude":1.0},{"latitude":1.0,"longitude":1.01}]},
                 "themes":["culture"]}
                """))
        .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value("inside")).andExpect(jsonPath("$[1].id").value("boundary"));
  }

  @Test
  void rejectsEmptyOrSelfIntersectingSelectionBeforeProviderWork() {
    assertInvalid(List.of());
    assertInvalid(List.of(new Point(1., 1.), new Point(1.01, 1.01),
        new Point(1., 1.01), new Point(1.01, 1.)));
    verifyNoInteractions(places);
  }

  @Test
  void filtersRectangleAndRadiusIndependentlyOfTheProviderEnvelope() {
    when(places.search(any())).thenReturn(List.of(place("inside", 1.001, 1.001),
        place("far", 1.005, 1.005)));
    for (var shape : List.of(
        new MapSelection(MapSelection.Shape.RECTANGLE, List.of(new Point(1., 1.), new Point(1.002, 1.002)), null),
        new MapSelection(MapSelection.Shape.CIRCLE, List.of(new Point(1., 1.)), 200.))) {
      assertThat(controller.search(new MapSelectionController.Query(shape, List.of("cultural"), "ru")))
          .extracting(Place::id).containsExactly("inside");
    }
  }

  private void assertInvalid(List<Point> coordinates) {
    var query = new MapSelectionController.Query(new MapSelection(MapSelection.Shape.POLYGON, coordinates, null),
        List.of("culture"), "ru");
    assertThatThrownBy(() -> controller.search(query)).isInstanceOf(ApiException.class)
        .hasMessage("journey.trip.invalid-map-selection");
  }

  private static Place place(String id, double latitude, double longitude) {
    return new Place(id, id, longitude, latitude, null, null, null);
  }
}
