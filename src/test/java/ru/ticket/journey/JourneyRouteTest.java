package ru.ticket.journey;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import ru.ticket.dto.*;

@DataJpaTest
class JourneyRouteTest {
  @Autowired JourneyRouteRepository routes;
  @Autowired JourneyMediaRepository assets;
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void immutableSnapshotIsDeduplicatedAndMapAndTimelineArePrintedTogether() throws Exception {
    var media = new JourneyMediaService(assets, 10);
    var map = media.save(JourneyMediaTest.image(2400, 1500, false));
    var service = new JourneyRouteService(routes, media, mapper);
    var snapshot = snapshot(map.id());
    var id = service.save(snapshot).id();
    assertThat(service.save(snapshot).id()).isEqualTo(id);
    assertThat(routes.count()).isEqualTo(1);
    assertThat(service.get(id)).isEqualTo(snapshot);
    var factory =
        new JourneyDocumentFactory(
            "codoc.site",
            mock(JourneySiteService.class),
            "https://www.codoc.site",
            new JourneyBrandingLayout(media),
            new JourneyRouteLayout(service, media));
    var view =
        new JourneyService.View(
            "route-test", JourneyService.Kind.TOUR, "Trip", Map.of("route", id), Instant.EPOCH);
    var document = factory.document(view);
    assertThat(document.slides).hasSize(2);
    assertThat(document.slides.get(1).elements)
        .anyMatch(
            element ->
                element instanceof ImageElement image
                    && image.id.equals("route-map")
                    && image.width == 694);
    assertThat(document.slides.get(1).elements)
        .filteredOn(element -> element instanceof TextElement)
        .anyMatch(element -> ((TextElement) element).text.contains("Airport"));
    assertThat(service.get(id).stops().get(0).description()).isEqualTo("Meet at the main entrance");
    assertThat(document.slides.stream().flatMap(slide -> slide.elements.stream()))
        .anyMatch(element -> element instanceof TextElement text && text.text.contains("Meet at the main entrance"));
  }

  @Test
  void invalidGeometryTimesAndExternalMapUrlsAreRejected() {
    var valid = snapshot("a".repeat(64));
    assertThatThrownBy(
            () ->
                JourneyRouteService.validate(
                    new JourneyRouteService.Snapshot(
                        valid.stops(),
                        valid.legs(),
                        valid.startLocal(),
                        valid.timeline(),
                        "https://external/image")))
        .hasMessage("journey.route.invalid-route");
    var invalid =
        new JourneyRouteService.Row(
            "car", "Bad", "2026-11-12T08:00:00Z", "2026-11-12T07:00:00Z", "Europe/Moscow");
    assertThatThrownBy(
            () ->
                JourneyRouteService.validate(
                    new JourneyRouteService.Snapshot(
                        valid.stops(),
                        valid.legs(),
                        valid.startLocal(),
                        List.of(invalid),
                        valid.mapId())))
        .hasMessage("journey.route.invalid-route");
    assertThatThrownBy(() -> BRouterRoadRouter.validate(new RoadRouter.Point(Double.NaN, 55)))
        .hasMessage("journey.route.invalid-route");
    assertThat(
            BRouterRoadRouter.tiles(new RoadRouter.Point(-.1, -.1), new RoadRouter.Point(.1, .1)))
        .containsExactlyInAnyOrder("W5_S5.rd5", "W5_N0.rd5", "E0_S5.rd5", "E0_N0.rd5");
  }

  @Test
  void longItineraryPaginatesInsteadOfDroppingRows() throws Exception {
    var media = new JourneyMediaService(assets, 10);
    var map = media.save(JourneyMediaTest.image(2400, 1500, false));
    var base = snapshot(map.id());
    List<JourneyRouteService.Row> rows = new ArrayList<>();
    for (int index = 0; index < 60; index++)
      rows.add(
          new JourneyRouteService.Row(
              "stop",
              "Stop " + index,
              "2026-11-12T07:00:00Z",
              "2026-11-12T07:00:00Z",
              "Europe/Moscow"));
    var service = new JourneyRouteService(routes, media, mapper);
    var id =
        service
            .save(
                new JourneyRouteService.Snapshot(
                    base.stops(), base.legs(), base.startLocal(), rows, map.id()))
            .id();
    var request = new ExportRequest();
    request.slides = List.of(new Slide());
    new JourneyRouteLayout(service, media)
        .append(
            request,
            new JourneyService.View(
                "test", JourneyService.Kind.TOUR, "Trip", Map.of("route", id), Instant.EPOCH));
    assertThat(request.slides.size()).isGreaterThan(5);
    assertThat(
            request.slides.stream()
                .flatMap(slide -> slide.elements.stream())
                .filter(element -> element instanceof TextElement)
                .map(element -> ((TextElement) element).text))
        .anyMatch(text -> text.contains("Stop 59"));
  }

  private static JourneyRouteService.Snapshot snapshot(String mapId) {
    return new JourneyRouteService.Snapshot(
        List.of(
            new JourneyRouteService.Stop("home", "Home", 13.388, 52.517, "Europe/Berlin", 0, "Meet at the main entrance"),
            new JourneyRouteService.Stop("airport", "Airport", 13.42, 52.51, "Europe/Berlin", 0)),
        List.of(
            new JourneyRouteService.Leg(
                "car",
                600,
                null,
                null,
                null,
                null,
                List.of(List.of(13.388, 52.517), List.of(13.42, 52.51)),
                3100.0)),
        "2026-11-12T08:00",
        List.of(
            new JourneyRouteService.Row(
                "car",
                "Home → Airport",
                "2026-11-12T07:00:00Z",
                "2026-11-12T07:10:00Z",
                "Europe/Berlin", null, "Meet at the main entrance")),
        mapId);
  }
}
