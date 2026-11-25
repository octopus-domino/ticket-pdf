package ru.ticket.journey.application.trip;

import java.time.LocalDate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.ticket.common.ApiException;
import ru.ticket.journey.application.journey.RouteSnapshotService;
import ru.ticket.journey.application.journey.JourneyService;
import ru.ticket.journey.application.journey.PrivateJourneyReader;
import ru.ticket.journey.domain.model.Trip;

/** Reads itineraries embedded in pre-planner publications and retains their original print snapshots. */
@Service
@ConditionalOnProperty(name = "app.journeys.trip-planner-enabled", havingValue = "true")
public class LegacyTrips {
  private final JourneyService publications;
  private final RouteSnapshotService routes;
  private final PrivateJourneyToTripConverter walkConverter;

  public LegacyTrips(JourneyService publications, RouteSnapshotService routes,
      PrivateJourneyToTripConverter walkConverter) {
    this.publications = publications;
    this.routes = routes;
    this.walkConverter = walkConverter;
  }

  /** Creates a previewable draft from the selected private walk. */
  public Trip importWalk(PrivateJourneyReader.Transfer walk, LocalDate date, String city) {
    return walkConverter.convert(walk, date, city);
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

  public Trip openLegacy(String ownerId, String id) {
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
