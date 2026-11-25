package ru.ticket.routing.web;

import org.springframework.web.bind.annotation.*;
import ru.ticket.routing.application.CalculateRoad;
import ru.ticket.routing.domain.Road;
import ru.ticket.routing.domain.RoadRequest;
import ru.ticket.routing.domain.RoutingFailure;
import static ru.ticket.routing.domain.RoutingFailure.Reason.INVALID_ROUTE;

@RestController
@RequestMapping("/api/routing/v1/road")
public class RoadController {
    public record Input(Coordinate start, Coordinate end, String mode) {}

    public record Coordinate(Double longitude, Double latitude) {
        RoadRequest.Point toPoint() {
            if (longitude == null || latitude == null) throw new RoutingFailure(INVALID_ROUTE);
            return new RoadRequest.Point(longitude, latitude);
        }
    }

    private final CalculateRoad calculateRoad;

    public RoadController(CalculateRoad calculateRoad) {
        this.calculateRoad = calculateRoad;
    }

    @PostMapping
    public Road calculate(@RequestBody Input input) {
        if (input.start() == null || input.end() == null) throw new RoutingFailure(INVALID_ROUTE);
        var mode = switch (input.mode() == null ? "" : input.mode()) {
            case "car" -> RoadRequest.Mode.CAR;
            case "foot" -> RoadRequest.Mode.FOOT;
            default -> throw new RoutingFailure(INVALID_ROUTE);
        };
        return calculateRoad.calculate(new RoadRequest(input.start().toPoint(), input.end().toPoint(), mode));
    }
}
