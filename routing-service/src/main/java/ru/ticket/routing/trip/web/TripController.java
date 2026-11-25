package ru.ticket.routing.trip.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.ticket.routing.trip.TripAccountGateway;
import ru.ticket.routing.trip.TripDrafts;

@RestController
@RequestMapping("/api/routing/v1/trips")
@ConditionalOnProperty(name = "app.journeys.trip-planner-enabled", havingValue = "true")
class TripController {
  private final TripDrafts drafts;
  private final TripAccountGateway accounts;

  TripController(TripDrafts drafts, TripAccountGateway accounts) {
    this.drafts = drafts;
    this.accounts = accounts;
  }

  @GetMapping
  ResponseEntity<TripDrafts.Trips> list(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
      @RequestParam(defaultValue = "0") int page) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(drafts.list(accounts.owner(auth), page));
  }

  @GetMapping("/{id}")
  ResponseEntity<JsonNode> open(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
      @PathVariable String id) {
    var owner = accounts.owner(auth);
    var result = drafts.find(owner, id).orElseGet(() -> accounts.legacyTrip(auth, id));
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
  }

  @PostMapping
  ResponseEntity<JsonNode> create(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
      @RequestBody JsonNode trip) {
    return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
        .body(drafts.save(accounts.owner(auth), trip.path("id").asText(), trip));
  }

  @PutMapping("/{id}")
  ResponseEntity<JsonNode> save(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
      @PathVariable String id, @RequestBody JsonNode trip) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(drafts.save(accounts.owner(auth), id, trip));
  }

  @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
  ResponseEntity<Void> conflict() {
    return ResponseEntity.status(HttpStatus.CONFLICT).cacheControl(CacheControl.noStore()).build();
  }
}
