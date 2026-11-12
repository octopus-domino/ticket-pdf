package ru.ticket.journey;

import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/journeys/routes")
public class RouteAddressController {
  private final AddressSearchService addresses;
  private final RoadRouter router;
  private final ru.ticket.common.HeavyOperationCapacity capacity;
  private final JourneyRouteService snapshots;

  public RouteAddressController(
      AddressSearchService addresses,
      RoadRouter router,
      ru.ticket.common.HeavyOperationCapacity capacity,
      JourneyRouteService snapshots) {
    this.addresses = addresses;
    this.router = router;
    this.capacity = capacity;
    this.snapshots = snapshots;
  }

  @GetMapping("/addresses")
  public List<AddressSearchService.Address> addresses(
      @RequestParam String q, @RequestParam(defaultValue = "en") String language) {
    return addresses.search(q, language);
  }

  public record RoadInput(RoadRouter.Point start, RoadRouter.Point end, String mode) {}

  @PostMapping
  public JourneyRouteService.Saved save(@RequestBody JourneyRouteService.Snapshot snapshot) {
    return snapshots.save(snapshot);
  }

  @GetMapping("/{id}")
  public JourneyRouteService.Snapshot snapshot(@PathVariable String id) {
    return snapshots.get(id);
  }

  @PostMapping("/road")
  public RoadRouter.Road road(@RequestBody RoadInput input) {
    if (input == null)
      throw ru.ticket.common.ApiException.badRequest("journey.route.invalid-route");
    if (!capacity.tryAcquire())
      throw new ru.ticket.common.ApiException(
          org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "journey.route.busy");
    try {
      return router.route(input.start(), input.end(), input.mode());
    } finally {
      capacity.release();
    }
  }
}
