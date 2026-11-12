package ru.ticket.journey;

import java.time.Duration;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import ru.ticket.common.ApiException;
import ru.ticket.common.HeavyOperationCapacity;

@RestController
@RequestMapping("/api/journeys/media")
public class JourneyMediaController {
  public record Input(String image) {}

  private final JourneyMediaService media;
  private final HeavyOperationCapacity capacity;

  public JourneyMediaController(JourneyMediaService media, HeavyOperationCapacity capacity) {
    this.media = media;
    this.capacity = capacity;
  }

  @PostMapping
  public JourneyMediaService.Asset upload(@RequestBody Input input) {
    if (!capacity.tryAcquire())
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "journey.media.busy");
    try {
      return media.save(input == null ? null : input.image());
    } finally {
      capacity.release();
    }
  }

  @GetMapping("/{id}")
  public ResponseEntity<byte[]> image(@PathVariable String id) {
    var asset = media.get(id);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(asset.contentType))
        .header("X-Content-Type-Options", "nosniff")
        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
        .body(asset.content);
  }
}
