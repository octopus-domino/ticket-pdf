package ru.ticket.journey;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import ru.ticket.auth.security.UserIdPrincipal;
import ru.ticket.common.*;

@RestController
@RequestMapping("/api/journeys/{id}/album")
public class JourneyAlbumController {
  private final JourneyAlbumService albums;
  private final HeavyOperationCapacity capacity;
  public JourneyAlbumController(JourneyAlbumService albums, HeavyOperationCapacity capacity) { this.albums = albums; this.capacity = capacity; }
  @GetMapping
  public JourneyAlbumService.State get(@PathVariable String id, @AuthenticationPrincipal UserIdPrincipal user,
      @RequestHeader(value = "X-Management-Key", required = false) String key) { return albums.get(id, owner(user), key); }
  @PostMapping("/invitation")
  public Map<String,String> invitation(@PathVariable String id, @AuthenticationPrincipal UserIdPrincipal user,
      @RequestHeader(value = "X-Management-Key", required = false) String key, @RequestParam(defaultValue = "false") boolean rotate) {
    return Map.of("key", albums.invitation(id, owner(user), key, rotate));
  }
  @PostMapping("/photos")
  public JourneyAlbumService.Added add(@PathVariable String id, @RequestHeader(value = "X-Contribution-Key", required = false) String key,
      @RequestBody JourneyAlbumService.Input input) {
    if (!capacity.tryAcquire()) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "journey.media.busy");
    try { return albums.add(id, key, input); } finally { capacity.release(); }
  }
  @PutMapping("/photos/{photoId}")
  public JourneyAlbumService.State edit(@PathVariable String id, @PathVariable String photoId, @AuthenticationPrincipal UserIdPrincipal user,
      @RequestHeader(value = "X-Management-Key", required = false) String key, @RequestHeader(value = "X-Photo-Edit-Key", required = false) String editKey,
      @RequestBody JourneyAlbumService.Edit input) { return albums.edit(id, photoId, owner(user), key, editKey, input, false); }
  @DeleteMapping("/photos/{photoId}")
  public JourneyAlbumService.State remove(@PathVariable String id, @PathVariable String photoId, @AuthenticationPrincipal UserIdPrincipal user,
      @RequestHeader(value = "X-Management-Key", required = false) String key, @RequestHeader(value = "X-Photo-Edit-Key", required = false) String editKey) {
    return albums.edit(id, photoId, owner(user), key, editKey, null, true);
  }
  @GetMapping("/photos/{photoId}")
  public ResponseEntity<byte[]> photo(@PathVariable String id, @PathVariable String photoId) {
    var image = albums.photo(id, photoId); return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType))
        .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").body(image.content);
  }
  @PutMapping("/order")
  public JourneyAlbumService.State order(@PathVariable String id, @AuthenticationPrincipal UserIdPrincipal user,
      @RequestHeader(value = "X-Management-Key", required = false) String key, @RequestBody JourneyAlbumService.Order order) {
    return albums.reorder(id, owner(user), key, order);
  }
  private static String owner(UserIdPrincipal user) { return user == null ? null : user.userId(); }
}
