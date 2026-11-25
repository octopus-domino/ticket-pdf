package ru.ticket.journey.web.journey;

import ru.ticket.journey.application.journey.RouteSnapshotService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/journeys/routes")
public class RouteSnapshotController {
  private final RouteSnapshotService snapshots;

  public RouteSnapshotController(RouteSnapshotService snapshots) {
    this.snapshots = snapshots;
  }

  @PostMapping
  public RouteSnapshotService.Saved save(@RequestBody RouteSnapshotService.Snapshot snapshot) {
    return snapshots.save(snapshot);
  }

  @GetMapping("/{id}")
  public RouteSnapshotService.Snapshot snapshot(@PathVariable String id) {
    return snapshots.get(id);
  }
}
