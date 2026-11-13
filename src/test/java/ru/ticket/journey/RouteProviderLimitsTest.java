package ru.ticket.journey;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class RouteProviderLimitsTest {
  @Test void carTransferOver400KmIsAdmittedButOver1500KmIsRejectedBeforeRouting() {
    var router = new BRouterRoadRouter(new ObjectMapper(), "target/nonexistent-router", "target/unused-graphs");
    var start = new RoadRouter.Point(37.6, 55.7);
    assertThatThrownBy(() -> router.route(start, new RoadRouter.Point(49.1, 55.8), "car"))
        .hasMessageContaining("journey.route.unavailable");
    assertThatThrownBy(() -> router.route(start, new RoadRouter.Point(80, 55), "car"))
        .hasMessageContaining("journey.route.too-long");
    assertThat(BRouterRoadRouter.tiles(start, new RoadRouter.Point(49.1, 55.8))).hasSizeGreaterThan(1);
  }
  @Test void discoveryUsesOsmWhenNoPaidKeyIsConfigured() {
    var osm = org.mockito.Mockito.mock(OsmPlaceService.class);
    org.mockito.Mockito.when(osm.search(37.6, 55.7, "attractions", "ru")).thenReturn(java.util.List.of());
    var controller = new PlaceRecommendationsController(new ObjectMapper(), "", osm);
    var result = controller.search(37.6, 55.7, "attractions", "ru");
    assertThat(result.available()).isTrue();
    assertThat(result.source()).isEqualTo("OpenStreetMap");
    assertThat(result.places()).isEmpty();
    assertThatThrownBy(() -> controller.search(Double.NaN, 55, "hotels", "ru"))
        .hasMessageContaining("journey.route.invalid-route");
  }
}
