package ru.ticket.journey;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/journeys/routes")
public class RouteSnapshotController {
  private final JourneyRouteService snapshots;

  public RouteSnapshotController(JourneyRouteService snapshots) {
    this.snapshots = snapshots;
  }

  @PostMapping
  public JourneyRouteService.Saved save(@RequestBody JourneyRouteService.Snapshot snapshot) {
    return snapshots.save(snapshot);
  }

  @GetMapping("/{id}")
  public JourneyRouteService.Snapshot snapshot(@PathVariable String id) {
    return snapshots.get(id);
  }
}
