package ru.ticket.journey.infrastructure.routing;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.ticket.journey.domain.service.RoadRouter;
import ru.ticket.journey.shared.RouteCoordinates;

class BRouterRoadRouterTest {
  @Test void carTransferOver400KmIsAdmittedButOver1500KmIsRejectedBeforeRouting() {
    var router = new BRouterRoadRouter(new ObjectMapper(), "target/nonexistent-router", "target/unused-graphs");
    var start = new RoadRouter.Point(37.6, 55.7);
    assertThatThrownBy(() -> router.route(start, new RoadRouter.Point(49.1, 55.8), "car"))
        .hasMessageContaining("journey.route.unavailable");
    assertThatThrownBy(() -> router.route(start, new RoadRouter.Point(80, 55), "car"))
        .hasMessageContaining("journey.route.too-long");
    assertThat(BRouterRoadRouter.tiles(start, new RoadRouter.Point(49.1, 55.8))).hasSizeGreaterThan(1);
  }
  @Test
  void validatesPointsAndFindsGraphTilesAcrossTheEquatorAndPrimeMeridian() {
    assertThatThrownBy(() -> RouteCoordinates.validate(new RoadRouter.Point(Double.NaN, 55)))
        .hasMessage("journey.route.invalid-route");
    assertThat(
            BRouterRoadRouter.tiles(new RoadRouter.Point(-.1, -.1), new RoadRouter.Point(.1, .1)))
        .containsExactlyInAnyOrder("W5_S5.rd5", "W5_N0.rd5", "E0_S5.rd5", "E0_N0.rd5");
  }
}
