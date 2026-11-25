package ru.ticket.walks.web;

import ru.ticket.walks.infrastructure.poi.OsmPlaceService;
import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class RouteProviderLimitsTest {
  @Test void discoveryUsesOsmWhenNoPaidKeyIsConfigured() {
    var osm = org.mockito.Mockito.mock(OsmPlaceService.class);
    org.mockito.Mockito.when(osm.search(37.6, 55.7, "attractions", "ru")).thenReturn(java.util.List.of());
    var controller = new PlaceRecommendationsController(new ObjectMapper(), "", osm);
    var result = controller.search(37.6, 55.7, "attractions", "ru");
    assertThat(result.available()).isTrue();
    assertThat(result.source()).isEqualTo("OpenStreetMap / Wikimedia");
    assertThat(result.places()).isEmpty();
    assertThatThrownBy(() -> controller.search(Double.NaN, 55, "hotels", "ru"))
        .hasMessageContaining("journey.route.invalid-route");
  }
}
