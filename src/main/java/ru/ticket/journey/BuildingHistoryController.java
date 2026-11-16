// ticket-pdf/src/main/java/ru/ticket/journey/BuildingHistoryController.java
package ru.ticket.journey;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

@RestController @RequestMapping("/api/journeys/buildings")
public class BuildingHistoryController {
  private final BuildingHistoryService service;
  public BuildingHistoryController(BuildingHistoryService service){this.service=service;}

  @GetMapping
  public ResponseEntity<BuildingHistoryService.Info> byPoint(
          @RequestParam double lon, @RequestParam double lat){
    return ResponseEntity.of(service.findByPoint(lon,lat).map(BuildingHistoryService::info));
  }

  @GetMapping("/nearby")
  public List<BuildingHistoryService.Info> nearby(
          @RequestParam double lon,
          @RequestParam double lat,
          @RequestParam(defaultValue="200") int limit,      // 12 → 200
          @RequestParam(defaultValue="1.5") double radiusKm,
          @RequestParam(defaultValue="") String q,
          @RequestParam(defaultValue="1945") int beforeYear){
    return service.nearby(lon,lat,limit,radiusKm,q,beforeYear);
  }
}