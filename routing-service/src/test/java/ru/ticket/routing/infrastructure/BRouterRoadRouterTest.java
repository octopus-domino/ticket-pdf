package ru.ticket.routing.infrastructure;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.ticket.routing.domain.RoadRequest;
import ru.ticket.routing.domain.RoadRequest.Point;
import ru.ticket.routing.domain.RoadRequest.Mode;

class BRouterRoadRouterTest {
  @Test void carTransferOver400KmIsAdmittedButOver1500KmIsRejectedBeforeRouting() {
    var router = new BRouterRoadRouter(new ObjectMapper(), "target/nonexistent-router", "target/unused-graphs");
    var start = new Point(37.6, 55.7);
    assertThatThrownBy(() -> router.calculate(new RoadRequest(start, new Point(49.1, 55.8), Mode.CAR)))
        .hasMessageContaining("journey.route.unavailable");
    assertThatThrownBy(() -> router.calculate(new RoadRequest(start, new Point(80, 55), Mode.CAR)))
        .hasMessageContaining("journey.route.too-long");
    assertThat(BRouterRoadRouter.tiles(start, new Point(49.1, 55.8))).hasSizeGreaterThan(1);
  }
  @Test
  void validatesPointsAndFindsGraphTilesAcrossTheEquatorAndPrimeMeridian() {
    assertThatThrownBy(() -> new Point(Double.NaN, 55))
        .hasMessage("journey.route.invalid-route");
    assertThat(
            BRouterRoadRouter.tiles(new Point(-.1, -.1), new Point(.1, .1)))
        .containsExactlyInAnyOrder("W5_S5.rd5", "W5_N0.rd5", "E0_S5.rd5", "E0_N0.rd5");
  }
}
