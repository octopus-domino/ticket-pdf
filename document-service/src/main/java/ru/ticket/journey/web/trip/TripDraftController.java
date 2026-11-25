package ru.ticket.journey.web.trip;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.ticket.auth.security.UserIdPrincipal;
import ru.ticket.common.ApiException;
import ru.ticket.journey.application.journey.PrivateJourneyReader;
import ru.ticket.journey.application.journey.JourneyPrintService;
import ru.ticket.journey.application.trip.TripDraftService;
import ru.ticket.journey.domain.model.Trip;
import ru.ticket.journey.infrastructure.calendar.TripCalendar;

/** Endpoints for explicit, account-scoped trip storage and walk conversion. */
@RestController
@RequestMapping("/api/trips")
@ConditionalOnProperty(name = "app.journeys.trip-planner-enabled", havingValue = "true")
public class TripDraftController {
  private final TripDraftService drafts;
  private final JourneyPrintService printing;

  public TripDraftController(TripDraftService drafts, JourneyPrintService printing) {
    this.drafts = drafts;
    this.printing = printing;
  }

  public record WalkImport(PrivateJourneyReader.Transfer walk, LocalDate startDate, String city) {}

  @GetMapping("/{id}")
  public ResponseEntity<Trip> open(@AuthenticationPrincipal UserIdPrincipal user, @PathVariable String id) {
    return privateResponse(drafts.openOwned(owner(user), id));
  }

  /** Exports either the v2 itinerary or the original snapshot of an owned legacy route. */
  @GetMapping("/{id}/json")
  public ResponseEntity<?> json(@AuthenticationPrincipal UserIdPrincipal user, @PathVariable String id,
      @RequestParam(defaultValue = "v2") String format) {
    String owner = owner(user);
    Object data = switch (format) {
      case "v2" -> drafts.openOwned(owner, id);
      case "v1-compat" -> drafts.openLegacySnapshot(owner, id);
      default -> throw ApiException.badRequest("journey.trip.invalid-export-format");
    };
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore())
        .header("Content-Disposition", format.equals("v2") ? "attachment; filename=trip-v2.json"
            : "attachment; filename=route-v1.json")
        .body(data);
  }

  /** Downloads calendar events from the account copy. */
  @GetMapping("/{id}/calendar")
  public ResponseEntity<String> calendar(@AuthenticationPrincipal UserIdPrincipal user, @PathVariable String id)
      throws IOException {
    Trip trip = drafts.openOwned(owner(user), id);
    if (trip.startDate() == null || trip.endDate() == null) throw ApiException.badRequest("journey.trip.invalid-date");
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .contentType(new MediaType("text", "calendar", StandardCharsets.UTF_8))
        .header("Content-Disposition", "attachment; filename=trip.ics")
        .body(TripCalendar.render(trip));
  }

  /** Renders the owner's saved trip through the shared PDF renderer. */
  @GetMapping("/{id}/pdf")
  public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal UserIdPrincipal user, @PathVariable String id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
        .header("Content-Disposition", "attachment; filename=trip.pdf")
        .body(printing.tripPdf(drafts.openOwned(owner(user), id)));
  }

  @PutMapping("/{id}")
  public ResponseEntity<Trip> save(@AuthenticationPrincipal UserIdPrincipal user, @PathVariable String id,
      @RequestBody Trip trip) {
    String owner = owner(user);
    if (trip == null || !id.equals(trip.id()) || trip.meta().mode() != Trip.Mode.TRIP) {
      throw ApiException.badRequest("journey.trip.invalid-data");
    }
    return privateResponse(drafts.saveOwned(owner, trip));
  }

  @PostMapping("/import-walk")
  public ResponseEntity<Trip> importWalk(@AuthenticationPrincipal UserIdPrincipal user, @RequestBody WalkImport input) {
    owner(user);
    if (input == null) throw ApiException.badRequest("journey.trip.invalid-private-walk");
    return privateResponse(drafts.importWalk(input.walk(), input.startDate(), input.city()));
  }

  private static String owner(UserIdPrincipal user) {
    if (user == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in");
    return user.userId();
  }

  private static ResponseEntity<Trip> privateResponse(Trip value) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
  }
}
