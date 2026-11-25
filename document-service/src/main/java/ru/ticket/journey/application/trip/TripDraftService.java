package ru.ticket.journey.application.trip;

import java.time.LocalDate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.ticket.common.ApiException;
import ru.ticket.journey.application.journey.RouteSnapshotService;
import ru.ticket.journey.application.journey.JourneyService;
import ru.ticket.journey.application.journey.PrivateJourneyReader;
import ru.ticket.journey.domain.model.Trip;
import ru.ticket.journey.domain.repository.TripRepository;

/** Loads private v2 drafts and projects accessible legacy itineraries on first read. */
@Service
@ConditionalOnProperty(name = "app.journeys.trip-planner-enabled", havingValue = "true")
public class TripDraftService {
  private final TripRepository trips;
  private final JourneyService publications;
  private final RouteSnapshotService routes;
  private final PrivateJourneyToTripConverter walkConverter;
  private final ApplicationEventPublisher events;

  /** Wires private v2 storage and the legacy publication readers. */
  public TripDraftService(TripRepository trips, JourneyService publications, RouteSnapshotService routes,
      PrivateJourneyToTripConverter walkConverter, ApplicationEventPublisher events) {
    this.trips = trips;
    this.publications = publications;
    this.routes = routes;
    this.walkConverter = walkConverter;
    this.events = events;
  }

  /** Creates a previewable draft from the selected private walk. */
  public Trip importWalk(PrivateJourneyReader.Transfer walk, LocalDate date, String city) {
    return walkConverter.convert(walk, date, city);
  }

  /** Returns the saved draft or an in-memory projection of an authorized legacy publication. */
  public Trip openOwned(String ownerId, String id) {
    return trips.findOwned(ownerId, id).orElseGet(() -> openLegacy(ownerId, id));
  }

  /** Returns the original v1 snapshot for an owned legacy itinerary. */
  public RouteSnapshotService.Snapshot openLegacySnapshot(String ownerId, String id) {
    var publication = publications.manageOwned(id, ownerId);
    String routeId = publication.data().get("route");
    if (publication.kind() != JourneyService.Kind.TOUR || routeId == null || routeId.isBlank()) {
      throw ApiException.notFound("Legacy route");
    }
    return routes.get(routeId);
  }

  /** Stores a draft after the user has explicitly requested server persistence. */
  public Trip saveOwned(String ownerId, Trip trip) {
    Trip saved = trips.saveOwned(ownerId, trip);
    events.publishEvent(new TripChangedEvent(saved.id()));
    return saved;
  }

  private Trip openLegacy(String ownerId, String id) {
    var publication = publications.manageOwned(id, ownerId);
    if (publication.kind() != JourneyService.Kind.TOUR) throw ApiException.notFound("Trip");
    String routeId = publication.data().get("route");
    return TripV1Migration.convert(publication, routeId == null || routeId.isBlank() ? null : route(routeId));
  }

  private RouteSnapshotService.Snapshot route(String id) {
    try {
      return routes.get(id);
    } catch (ApiException invalid) {
      if (invalid.status() == HttpStatus.BAD_REQUEST || invalid.status() == HttpStatus.NOT_FOUND) return null;
      throw invalid;
    }
  }
}
