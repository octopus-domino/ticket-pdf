package ru.ticket.journey;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import ru.ticket.auth.security.UserIdPrincipal;
import ru.ticket.common.ApiException;
import ru.ticket.common.HeavyOperationCapacity;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/publications")
public class JourneyConstructorController {
  private final JourneyConstructorCopyService copies;
  private final HeavyOperationCapacity capacity;
  private final JourneyPrintSourceService sources;
  public JourneyConstructorController(JourneyConstructorCopyService copies, HeavyOperationCapacity capacity, JourneyPrintSourceService sources) {
    this.copies = copies; this.capacity = capacity; this.sources = sources;
  }
  @PostMapping("/{id}/constructor")
  public JourneyConstructorCopyService.Created copy(@AuthenticationPrincipal UserIdPrincipal user, @PathVariable String id,
      @RequestHeader(value = "X-Management-Key", required = false) String key) {
    if (user == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in");
    if (!capacity.tryAcquire()) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "journey.route.busy");
    try { return copies.copy(user.userId(), id, key); } finally { capacity.release(); }
  }
  @PutMapping("/{id}/constructor")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void apply(@AuthenticationPrincipal UserIdPrincipal user, @PathVariable String id,
      @RequestHeader(value = "X-Management-Key", required = false) String key, @RequestBody JourneyPrintSourceService.Input input) {
    if (user == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in");
    if (!capacity.tryAcquire()) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "journey.route.busy");
    try { sources.publication(user.userId(), id, key, input); } finally { capacity.release(); }
  }
  @PutMapping("/sites/{slug}/constructor")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void applySite(@AuthenticationPrincipal UserIdPrincipal user, @PathVariable String slug, @RequestBody JourneyPrintSourceService.Input input) {
    if (user == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in");
    if (!capacity.tryAcquire()) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "journey.route.busy");
    try { sources.site(user.userId(), slug, input); } finally { capacity.release(); }
  }
  @PostMapping("/{id}/constructor/publications")
  public JourneyService.Created createPublication(@AuthenticationPrincipal UserIdPrincipal user, @PathVariable String id,
      @RequestHeader(value = "X-Management-Key", required = false) String key, @RequestBody JourneyPrintSourceService.Input input) {
    if (user == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in");
    if (!capacity.tryAcquire()) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "journey.route.busy");
    try { return sources.createPublication(user.userId(), id, key, input); } finally { capacity.release(); }
  }
  @PutMapping("/batches/{id}/constructor")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void batch(@AuthenticationPrincipal UserIdPrincipal user, @PathVariable String id,
      @RequestHeader(value = "X-Management-Key", required = false) String key, @RequestBody JourneyPrintSourceService.Input input) {
    if (user == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in");
    if (!capacity.tryAcquire()) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "journey.route.busy");
    try { sources.batch(user.userId(), id, key, input); } finally { capacity.release(); }
  }
}
