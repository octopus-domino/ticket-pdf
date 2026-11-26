package ru.ticket.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ru.ticket.journey.infrastructure.media.JourneyMediaService;
import ru.ticket.journey.application.journey.RouteSnapshotService;
import ru.ticket.journey.domain.model.Trip;
import ru.ticket.journey.infrastructure.RoutingTripClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:contract;DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "jwt.secret=contract-test-secret-only-012345678901234567890123456789",
      "app.rate-limit.enabled=false",
      "app.journeys.trip-planner-enabled=true",
      "spring.security.oauth2.client.registration.google.client-id=test",
      "spring.security.oauth2.client.registration.google.client-secret=test",
      "spring.security.oauth2.client.provider.google.issuer-uri=https://accounts.google.com",
      "SMTP_HOST=localhost",
      "SMTP_USER=test@example.invalid",
      "EMAIL_FROM=test@example.invalid",
    })
class DocumentFlowTest {
  private static final java.nio.file.Path ARTIFACT_ROOT =
      java.nio.file.Path.of("target", "test-artifacts", "run-" + java.util.UUID.randomUUID());
  @Autowired RouteSnapshotService tripRoutes;
  @Autowired JourneyMediaService tripMedia;
  @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
  @MockBean RoutingTripClient routingTrips;

  @Test
  void documentServiceRejectsTheRemovedRoadCalculationMethod() {
    var response = http.postForEntity("/api/journeys/routes/road", Map.of(), String.class);

    assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode(), response.toString());
    assertTrue(response.getHeaders().getAllow().contains(HttpMethod.GET));
    assertFalse(response.getHeaders().getAllow().contains(HttpMethod.POST));
  }

  @Test
  void opensALegacyWalkWithGeometryAndKeepsTheOriginalPublicationPrintable() throws Exception {
    var headers = registeredHeaders("legacy-trip-owner");
    var image = new java.awt.image.BufferedImage(32, 24, java.awt.image.BufferedImage.TYPE_INT_RGB);
    var png = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(image, "png", png);
    var map = tripMedia.save("data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray()));
    var stops = List.of(
        new RouteSnapshotService.Stop("a", "Museum", 37.62, 55.75, "Europe/Moscow", 0),
        new RouteSnapshotService.Stop("b", "Park", 37.63, 55.76, "Europe/Moscow", 0));
    var geometry = List.of(List.of(37.62, 55.75), List.of(37.63, 55.76));
    var route = new RouteSnapshotService.Snapshot(stops,
        List.of(new RouteSnapshotService.Leg("foot", 900, null, null, null, null, geometry, 1500d)),
        "2027-01-01T10:00", List.of(new RouteSnapshotService.Row("foot", "Museum to park",
            "2027-01-01T07:00:00Z", "2027-01-01T07:15:00Z", "Europe/Moscow")), map.id());
    String routeId = tripRoutes.save(route).id();
    var input = new ru.ticket.journey.application.journey.JourneyService.Input(ru.ticket.journey.application.journey.JourneyService.Kind.TOUR,
        "Legacy walk", Map.of("route", routeId, "city", "Moscow"), List.of());
    var created = http.postForEntity("/api/journeys", new HttpEntity<>(input, headers),
        ru.ticket.journey.application.journey.JourneyService.Created[].class);
    assertEquals(200, created.getStatusCode().value(), created.toString());
    String id = created.getBody()[0].document().id();
    var projected = http.exchange("/api/trips/legacy/" + id, HttpMethod.GET, new HttpEntity<>(headers),
        ru.ticket.journey.domain.model.Trip.class);
    assertEquals(200, projected.getStatusCode().value(), projected.toString());
    assertEquals(routeId, projected.getBody().meta().legacyRouteId());
    assertEquals(2, projected.getBody().days().get(0).walkingSections().get(0).geometry().size());
    assertEquals(geometry, tripRoutes.get(routeId).legs().get(0).coordinates());
    var originalJson = http.exchange("/api/trips/" + id + "/json?format=v1-compat", HttpMethod.GET,
        new HttpEntity<>(headers), RouteSnapshotService.Snapshot.class);
    assertEquals(200, originalJson.getStatusCode().value());
    assertEquals("no-store", originalJson.getHeaders().getCacheControl());
    assertEquals(tripRoutes.get(routeId), originalJson.getBody());
    var original = http.getForEntity("/api/journeys/" + id + "/pdf", byte[].class);
    assertEquals(200, original.getStatusCode().value());
    try (var pdf = org.apache.pdfbox.Loader.loadPDF(original.getBody())) {
      assertTrue(pdf.getNumberOfPages() > 0);
    }
  }

  @Test
  void exportsTheOwnedRoutingTripToPrivatePdfCalendarAndJson() throws Exception {
    var owner = registeredHeaders("trip-export-owner");
    var other = registeredHeaders("trip-export-other");
    owner.setContentType(MediaType.APPLICATION_JSON);
    String path = "/api/trips/contract-trip";
    String itinerary = """
        {"version":2,"id":"contract-trip","title":"Winter itinerary","startDate":"2027-01-01","endDate":"2027-01-02",
         "baseCurrency":"RUB","travelers":[{"name":"Private traveler","email":"private@example.invalid"}],
         "cities":[{"id":"moscow","name":"Moscow"},{"id":"yen","name":"Yekaterinburg"}],
         "days":[{"date":"2027-01-01","cityId":"moscow","note":"River walk","accommodationId":"hotel","segments":[]}],
         "legs":[{"id":"train","fromCityId":"moscow","toCityId":"yen","transport":"TRAIN",
           "departureAt":"2027-01-01T10:00:00+03:00","arrivalAt":"2027-01-01T16:00:00+05:00","note":"Train 014",
           "cost":{"amount":1900,"currency":"RUB"}}],
         "accommodations":[{"id":"hotel","cityId":"yen","name":"Station Hotel","address":"Main Street",
           "checkIn":"2027-01-01","checkOut":"2027-01-02","provider":"MANUAL","cost":{"amount":80,"currency":"EUR"}}],
         "documents":[{"id":"reservation","title":"Rail reservation","url":"https://example.invalid/reservation"}],
         "budget":{"limit":{"amount":12500,"currency":"RUB"}},"meta":{"mode":"TRIP","source":"MANUAL"}}
        """;
    org.mockito.Mockito.when(routingTrips.read("contract-trip", owner.getFirst(HttpHeaders.AUTHORIZATION)))
        .thenReturn(mapper.readValue(itinerary, Trip.class));
    org.mockito.Mockito.when(routingTrips.read("contract-trip", other.getFirst(HttpHeaders.AUTHORIZATION)))
        .thenThrow(ru.ticket.common.ApiException.notFound("Trip"));
    var v2 = http.exchange(path + "/json", HttpMethod.GET, new HttpEntity<>(owner), String.class);
    assertEquals(200, v2.getStatusCode().value());
    assertTrue(v2.getBody().contains("+03:00") && v2.getBody().contains("+05:00"));
    assertEquals("no-store", v2.getHeaders().getCacheControl());
    for (String suffix : List.of("/pdf", "/calendar", "/json", "/json?format=v1-compat")) {
      var rejected = http.exchange(path + suffix, HttpMethod.GET, new HttpEntity<>(other), String.class);
      assertTrue(rejected.getStatusCode().is4xxClientError(), rejected.toString());
    }
    var pdf = http.exchange(path + "/pdf", HttpMethod.GET, new HttpEntity<>(owner), byte[].class);
    assertEquals(200, pdf.getStatusCode().value());
    assertEquals(MediaType.APPLICATION_PDF, pdf.getHeaders().getContentType());
    try (var parsed = org.apache.pdfbox.Loader.loadPDF(pdf.getBody())) {
      String text = new org.apache.pdfbox.text.PDFTextStripper().getText(parsed);
      for (String item : List.of("Winter itinerary", "River walk", "Train 014", "+03:00", "+05:00",
          "Station Hotel", "1900 RUB", "80 EUR", "12500", "Rail reservation")) assertTrue(text.contains(item), text);
      assertFalse(text.contains("private@example.invalid"));
    }
    var calendar = http.exchange(path + "/calendar", HttpMethod.GET, new HttpEntity<>(owner), String.class);
    assertEquals(200, calendar.getStatusCode().value());
    assertEquals("no-store", calendar.getHeaders().getCacheControl());
    String ics = calendar.getBody();
    var parsed = new net.fortuna.ical4j.data.CalendarBuilder().build(new java.io.StringReader(ics));
    assertEquals(4, parsed.getComponents().size());
    assertTrue(ics.contains("DTSTART:20270101T070000Z"));
    assertTrue(ics.contains("DTEND:20270101T110000Z"));
    assertFalse(ics.contains("private@example.invalid"));
  }

  @Test
  void printsTheSavedWalkWithNumberedStopsAndOnlyAvailableLicensedPhotos() throws Exception {
    var owner = registeredHeaders("walk-photo-export");
    var png = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(48, 32, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", png);
    var asset = tripMedia.save("data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray()));
    String missing = "a".repeat(64);
    String itinerary = """
        {"version":2,"id":"photo-trip","title":"Photo itinerary","startDate":"2027-01-01","endDate":"2027-01-02",
         "travelers":[],"cities":[{"id":"city","name":"Berlin"}],"accommodations":[],"documents":[],"legs":[],
         "days":[{"date":"2027-01-02","cityId":"city","note":"Museum walk","segments":[
           {"id":"s1","title":"Museum","note":"Meet at the north entrance","photoRefs":["%s"],"photoCredits":[
             {"stopId":"s1","imageId":"%s","author":"Fixture Author","license":"CC BY 4.0",
              "licenseUrl":"https://creativecommons.org/licenses/by/4.0/","sourceUrl":"https://commons.wikimedia.org/wiki/File:Museum.jpg"}]},
           {"id":"s2","title":"Tower","photoRefs":["%s"],"photoCredits":[
             {"stopId":"s2","imageId":"%s","author":"Missing image author","license":"CC BY 4.0",
              "licenseUrl":"https://creativecommons.org/licenses/by/4.0/","sourceUrl":"https://commons.wikimedia.org/wiki/File:Missing.jpg"}]},
           {"id":"s3","title":"Bridge","photoRefs":["%s"],"photoCredits":[
             {"stopId":"s3","imageId":"%s","author":"Unlicensed author","sourceUrl":"https://commons.wikimedia.org/wiki/File:Bridge.jpg"}]}],
           "walkingSections":[
             {"fromSegmentId":"s1","toSegmentId":"s2","geometry":[{"latitude":52.50,"longitude":13.40},{"latitude":52.51,"longitude":13.41}]},
             {"fromSegmentId":"s2","toSegmentId":"s3","geometry":[{"latitude":52.51,"longitude":13.41},{"latitude":52.51,"longitude":13.42}]}]}],
         "meta":{"mode":"TRIP","source":"PRIVATE_JOURNEY"}}
        """.formatted(asset.id(), asset.id(), missing, missing, asset.id(), asset.id());
    var saved = mapper.readTree(itinerary);
    var day = (com.fasterxml.jackson.databind.node.ObjectNode) saved.withArray("days").get(0);
    for (int number = 4; number <= 9; number++) {
      String stopId = "s" + number;
      var stop = day.withArray("segments").addObject().put("id", stopId).put("title", "Walk stop " + number);
      stop.putArray("photoRefs").add(asset.id());
      stop.putArray("photoCredits").addObject().put("stopId", stopId).put("imageId", asset.id())
          .put("author", "Photographer " + number).put("license", "CC BY 4.0")
          .put("licenseUrl", "https://creativecommons.org/licenses/by/4.0/")
          .put("sourceUrl", "https://commons.wikimedia.org/wiki/File:Walk_" + number + ".jpg");
      var section = day.withArray("walkingSections").addObject()
          .put("fromSegmentId", "s" + (number - 1)).put("toSegmentId", stopId).putArray("geometry");
      section.addObject().put("latitude", 52.51).put("longitude", 13.42 + (number - 4) * 0.01);
      section.addObject().put("latitude", 52.51).put("longitude", 13.42 + (number - 3) * 0.01);
    }
    org.mockito.Mockito.when(routingTrips.read("photo-trip", owner.getFirst(HttpHeaders.AUTHORIZATION)))
        .thenReturn(mapper.treeToValue(saved, Trip.class));
    var response = http.exchange("/api/trips/photo-trip/pdf", HttpMethod.GET, new HttpEntity<>(owner), byte[].class);
    assertEquals(200, response.getStatusCode().value());
    try (var pdf = org.apache.pdfbox.Loader.loadPDF(response.getBody())) {
      var text = new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
      for (String item : List.of("1. Museum", "2. Tower", "3. Bridge", "Meet at the north entrance", "Fixture Author", "CC BY 4.0"))
        assertTrue(text.contains(item), text);
      for (int number = 4; number <= 9; number++) {
        assertTrue(text.contains(number + ". Walk stop " + number), text);
        assertTrue(text.contains("Photographer " + number), text);
      }
      assertTrue(pdf.getNumberOfPages() > 2);
      assertFalse(text.contains("Missing image author"));
      assertFalse(text.contains("Unlicensed author"));
      boolean containsPhoto = false;
      for (var page : pdf.getPages()) for (var name : page.getResources().getXObjectNames())
        if (page.getResources().isImageXObject(name)) containsPhoto = true;
      assertTrue(containsPhoto);
    }
  }

  @Test
  void newWorkflowsExposeProtectedIssuanceAndPrintableTables() throws Exception {
    var input =
        new ru.ticket.journey.application.journey.JourneyService.Input(
            ru.ticket.journey.application.journey.JourneyService.Kind.GIFT,
            "Gift",
            Map.of("amount", "1000", "valid_until", "2100-01-01"),
            List.of("Guest"));
    var issued =
        http.postForEntity(
            "/api/journeys", input, ru.ticket.journey.application.journey.JourneyService.Created[].class);
    assertEquals(200, issued.getStatusCode().value(), issued.toString());
    var created = issued.getBody()[0];
    String root = "/api/journeys/" + created.document().id();
    assertEquals(
        403, http.postForEntity(root + "/consume", null, String.class).getStatusCode().value());
    var headers = new HttpHeaders();
    headers.set("X-Management-Key", created.managementKey());
    assertEquals(
        200,
        http.exchange(root + "/consume", HttpMethod.POST, new HttpEntity<>(headers), String.class)
            .getStatusCode()
            .value());
    assertEquals(
        409,
        http.exchange(root + "/consume", HttpMethod.POST, new HttpEntity<>(headers), String.class)
            .getStatusCode()
            .value());
    String batch = "/api/journeys/batches/" + created.batchId();
    assertEquals(403, http.getForEntity(batch, String.class).getStatusCode().value());
    assertEquals(
        200,
        http.exchange(batch, HttpMethod.GET, new HttpEntity<>(headers), String.class)
            .getStatusCode()
            .value());
    var badge =
        http.postForEntity(
                "/api/journeys",
                new ru.ticket.journey.application.journey.JourneyService.Input(
                    ru.ticket.journey.application.journey.JourneyService.Kind.BADGE,
                    "Conference",
                    Map.of("issuer", "CoDoc"),
                    List.of("A", "B", "C", "D", "E", "F", "G")),
                ru.ticket.journey.application.journey.JourneyService.Created[].class)
            .getBody()[0];
    headers.set("X-Management-Key", badge.managementKey());
    var pdf =
        http.exchange(
            "/api/journeys/batches/" + badge.batchId() + "/pdf",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            byte[].class);
    assertEquals(200, pdf.getStatusCode().value());
    try (var parsed = org.apache.pdfbox.Loader.loadPDF(pdf.getBody())) {
      assertEquals(2, parsed.getNumberOfPages());
    }
    var proposal =
        http.postForEntity(
                "/api/journeys",
                new ru.ticket.journey.application.journey.JourneyService.Input(
                    ru.ticket.journey.application.journey.JourneyService.Kind.PROPOSAL,
                    "Proposal",
                    Map.of(
                        "positions",
                        "["
                            + java.util.stream.IntStream.range(0, 35)
                                .mapToObj(
                                    i ->
                                        "{\"name\":\"Product "
                                            + i
                                            + " "
                                            + "long name ".repeat(10)
                                            + "\",\"quantity\":\"1\",\"price\":\"10.01\"}")
                                .collect(java.util.stream.Collectors.joining(","))
                            + "]"),
                    List.of()),
                ru.ticket.journey.application.journey.JourneyService.Created[].class)
            .getBody()[0];
    var proposalPdf =
        http.getForEntity("/api/journeys/" + proposal.document().id() + "/pdf", byte[].class);
    assertEquals(200, proposalPdf.getStatusCode().value());
    try (var parsed = org.apache.pdfbox.Loader.loadPDF(proposalPdf.getBody())) {
      var artifactRoot = ARTIFACT_ROOT;
      Files.createDirectories(artifactRoot);
      Files.write(artifactRoot.resolve("proposal.pdf"), proposalPdf.getBody());
      var jsonMapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
      Files.write(
          artifactRoot.resolve("proposal-document.json"),
          jsonMapper.writeValueAsBytes(proposal.document()));
      var proposalArtwork =
          http.postForEntity(
              "/api/journeys/artwork",
              new ru.ticket.journey.web.journey.JourneyArtworkController.Input(
                  proposal.document().id(),
                  proposal.document().kind(),
                  proposal.document().title(),
                  proposal.document().data()),
              ru.ticket.journey.web.journey.JourneyArtworkController.Artwork.class);
      assertEquals(200, proposalArtwork.getStatusCode().value());
      assertEquals(parsed.getNumberOfPages(), proposalArtwork.getBody().pages().size());
      Files.write(
          artifactRoot.resolve("proposal-artwork.json"),
          jsonMapper.writeValueAsBytes(proposalArtwork.getBody()));
      javax.imageio.ImageIO.write(
          new org.apache.pdfbox.rendering.PDFRenderer(parsed).renderImageWithDPI(0, 96),
          "png",
          artifactRoot.resolve("proposal-first-page.png").toFile());
      assertTrue(parsed.getNumberOfPages() > 1);
      String text = new org.apache.pdfbox.text.PDFTextStripper().getText(parsed);
      assertTrue(text.contains("350,35"));
      assertTrue(text.contains("Product 34"));
      assertTrue(text.contains("Наименование"));
      assertTrue(text.contains("ИТОГО"));
    }
  }

  @org.springframework.boot.test.context.TestConfiguration
  static class OAuthConfiguration {
    @org.springframework.context.annotation.Bean
    org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
        clientRegistrationRepository() {
      var client =
          org.springframework.security.oauth2.client.registration.ClientRegistration
              .withRegistrationId("google")
              .clientId("test")
              .clientSecret("test")
              .authorizationGrantType(
                  org.springframework.security.oauth2.core.AuthorizationGrantType
                      .AUTHORIZATION_CODE)
              .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
              .authorizationUri("https://example.invalid/authorize")
              .tokenUri("https://example.invalid/token")
              .userInfoUri("https://example.invalid/userinfo")
              .userNameAttributeName("sub")
              .build();
      return new org.springframework.security.oauth2.client.registration
          .InMemoryClientRegistrationRepository(client);
    }
  }

  @Autowired TestRestTemplate http;
  @Autowired ru.ticket.journey.infrastructure.pdf.JourneyDocumentFactory journeyDocuments;
  @Autowired ru.ticket.svg.SvgDocumentBuilder journeySvg;

  @Test
  void browserPreviewLinksTheBackgroundAndOmitsTheOrnamentWhenBranded() throws Exception {
    var image = new java.awt.image.BufferedImage(256, 256, java.awt.image.BufferedImage.TYPE_INT_RGB);
    var random = new java.util.Random(123);
    for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) image.setRGB(x, y, random.nextInt(1 << 24));
    var bytes = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(image, "png", bytes);
    String png = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(bytes.toByteArray());
    var upload = http.postForEntity("/api/journeys/media/background", Map.of("image", png), Map.class);
    assertEquals(200, upload.getStatusCode().value());
    String id = (String) upload.getBody().get("id");
    var data = Map.of("description", "An invitation to celebrate", "background_media", id);
    var kind = ru.ticket.journey.application.journey.JourneyService.Kind.INVITATION;
    var type = ru.ticket.journey.web.journey.JourneyArtworkController.Artwork.class;
    var ordinary = http.postForEntity("/api/journeys/artwork",
        new ru.ticket.journey.web.journey.JourneyArtworkController.Input(null, kind, "Party", data), type);
    var linked = http.postForEntity("/api/journeys/artwork",
        new ru.ticket.journey.web.journey.JourneyArtworkController.Input(null, kind, "Party", data, null, true), type);
    assertEquals(200, ordinary.getStatusCode().value());
    assertEquals(200, linked.getStatusCode().value());
    assertEquals(id, linked.getBody().background().media());
    assertTrue(ordinary.getBody().pages().get(0).svg().contains("data:image/png;base64,"));
    String foreground = linked.getBody().pages().get(0).svg();
    assertTrue(foreground.contains("M 584 144"));
    assertFalse(foreground.contains("data:image/"));
    assertTrue(foreground.length() * 8 < ordinary.getBody().pages().get(0).svg().length());
    var asset = http.getForEntity("/api/journeys/media/" + id, byte[].class);
    assertEquals(200, asset.getStatusCode().value());
    assertTrue(asset.getHeaders().getCacheControl().contains("immutable"));

    var placement = new ru.ticket.journey.shared.JourneyBranding.Placement(id, 1, 0, .12);
    var branding = new ru.ticket.journey.shared.JourneyBranding(placement, null);
    var branded = Map.of("description", data.get("description"), "background_media", id, "branding",
        new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(branding));
    var response = http.postForEntity("/api/journeys/artwork",
        new ru.ticket.journey.web.journey.JourneyArtworkController.Input(null, kind, "Party", branded, null, true), type);
    assertEquals(200, response.getStatusCode().value());
    assertFalse(response.getBody().pages().get(0).svg().contains("M 584 144"));
    assertTrue(response.getBody().pages().get(0).svg().contains("data:image/png;base64,"));
    var printable = journeyDocuments.document(new ru.ticket.journey.application.journey.JourneyService.View(
        "preview", kind, "Party", branded, java.time.Instant.EPOCH)).slides.get(0);
    assertTrue(printable.elements.stream().anyMatch(element -> "company-logo".equals(element.id)));
    assertTrue(printable.elements.stream().noneMatch(element -> "event-branch".equals(element.id)));
    assertTrue(printable.backgroundImage.startsWith("data:image/png;base64,"));
  }

  @Test
  void eventArtworkUsesExactlyThePdfCompositionAndExportsReviewArtifacts() throws Exception {
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
    var dir = ARTIFACT_ROOT.resolve("events");
    Files.createDirectories(dir);
    for (String theme : List.of("rose", "sage", "ink")) {
      var data =
          Map.of(
              "theme",
              theme,
              "recipient",
              "Анна и Михаил",
              "description",
              "Будем рады провести этот вечер вместе с вами.\n"
                  + "Приходите на праздник — будем общаться, танцевать и создавать тёплые"
                  + " воспоминания.",
              "date",
              "2026-12-12",
              "time",
              "18:30",
              "location",
              "Усадьба «Липовый сад»",
              "contact",
              "Пожалуйста, подтвердите участие до 5 декабря.");
      var input =
          new ru.ticket.journey.application.journey.JourneyService.Input(
              ru.ticket.journey.application.journey.JourneyService.Kind.INVITATION,
              "Вечер в кругу близких",
              data,
              List.of());
      var created =
          http.postForEntity(
              "/api/journeys", input, ru.ticket.journey.application.journey.JourneyService.Created[].class);
      assertEquals(200, created.getStatusCode().value());
      var view = created.getBody()[0].document();
      var artwork =
          http.postForEntity(
              "/api/journeys/artwork",
              new ru.ticket.journey.web.journey.JourneyArtworkController.Input(
                  view.id(), view.kind(), view.title(), view.data()),
              ru.ticket.journey.web.journey.JourneyArtworkController.Artwork.class);
      assertEquals(200, artwork.getStatusCode().value());
      assertNotNull(artwork.getBody());
      var slides = journeyDocuments.document(view).slides;
      assertEquals(slides.size(), artwork.getBody().pages().size());
      for (int i = 0; i < slides.size(); i++)
        assertEquals(journeySvg.generate(slides.get(i)), artwork.getBody().pages().get(i).svg());
      var pdf = http.getForEntity("/api/journeys/" + view.id() + "/pdf", byte[].class);
      assertEquals(200, pdf.getStatusCode().value());
      Files.write(dir.resolve(theme + ".pdf"), pdf.getBody());
      Files.writeString(dir.resolve(theme + ".json"), mapper.writeValueAsString(artwork.getBody()));
      Files.writeString(dir.resolve(theme + "-document.json"), mapper.writeValueAsString(view));
      try (var parsed = org.apache.pdfbox.Loader.loadPDF(pdf.getBody())) {
        String text = new org.apache.pdfbox.text.PDFTextStripper().getText(parsed);
        assertTrue(text.contains("Анна и Михаил"));
        assertTrue(text.contains("12 декабря 2026"));
        assertFalse(text.contains("2026-12-12"));
        javax.imageio.ImageIO.write(
            new org.apache.pdfbox.rendering.PDFRenderer(parsed).renderImageWithDPI(0, 96),
            "png",
            dir.resolve(theme + "-pdf.png").toFile());
      }
    }
    var font = http.getForEntity("/api/journeys/fonts/serif", byte[].class);
    assertEquals(200, font.getStatusCode().value());
    assertTrue(font.getBody().length > 100000);
    assertEquals(
        404,
        http.getForEntity("/api/journeys/fonts/unknown", String.class).getStatusCode().value());
  }

  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
  @Autowired ru.ticket.generation.infrastructure.GenerationFiles generationFiles;

  @Test
  void publicWorkflowCreatesPrintablePdfProtectsRepliesAndRevokesPublication() {
    var input =
        new ru.ticket.journey.application.journey.JourneyService.Input(
            ru.ticket.journey.application.journey.JourneyService.Kind.INVITATION,
            "Birthday",
            Map.of("description", "Welcome friends", "theme", "rose"),
            List.of());
    var created =
        http.postForEntity(
            "/api/journeys", input, ru.ticket.journey.application.journey.JourneyService.Created[].class);
    assertEquals(200, created.getStatusCode().value(), created.toString());
    var publication = created.getBody()[0];
    String path = "/api/journeys/" + publication.document().id();
    var publicView = http.getForEntity(path, String.class);
    assertEquals(200, publicView.getStatusCode().value());
    assertFalse(publicView.getBody().contains(publication.managementKey()));
    assertEquals(403, http.getForEntity(path + "/responses", String.class).getStatusCode().value());
    var reply =
        new ru.ticket.journey.application.journey.JourneyService.Reply(
            "Guest", true, "response-key-with-at-least-32-characters");
    assertEquals(
        204, http.postForEntity(path + "/responses", reply, String.class).getStatusCode().value());
    var key = new HttpHeaders();
    key.set("X-Management-Key", publication.managementKey());
    var guests =
        http.exchange(path + "/responses", HttpMethod.GET, new HttpEntity<>(key), String.class);
    assertTrue(guests.getBody().contains("Guest"));
    var pdf = http.getForEntity(path + "/pdf", byte[].class);
    assertEquals(200, pdf.getStatusCode().value(), pdf.toString());
    assertEquals(MediaType.APPLICATION_PDF, pdf.getHeaders().getContentType());
    assertTrue(
        new String(pdf.getBody(), 0, 5, java.nio.charset.StandardCharsets.US_ASCII)
            .startsWith("%PDF-"));
    assertEquals(
        200,
        http.exchange(path, HttpMethod.DELETE, new HttpEntity<>(key), String.class)
            .getStatusCode()
            .value());
    assertEquals(410, http.getForEntity(path, String.class).getStatusCode().value());
  }

  @Test
  void ownProfileCanBeEditedAndOtherAccountsKeepTheirOwnProfile() {
    HttpHeaders owner = registeredHeaders("profile-owner"),
        other = registeredHeaders("profile-other");
    var saved =
        http.exchange(
            "/api/profile",
            HttpMethod.PUT,
            new HttpEntity<>(
                Map.of(
                    "name",
                    "Updated name",
                    "organization",
                    "Studio",
                    "position",
                    "Designer",
                    "phone",
                    "+7 000 000-00-00",
                    "avatar",
                    ""),
                owner),
            Map.class);
    assertEquals(200, saved.getStatusCode().value(), saved.toString());
    assertEquals("Updated name", saved.getBody().get("name"));
    assertEquals("Studio", saved.getBody().get("organization"));
    var isolated =
        http.exchange("/api/profile", HttpMethod.GET, new HttpEntity<>(other), Map.class);
    assertEquals("", isolated.getBody().get("organization"));
    assertNotEquals(saved.getBody().get("id"), isolated.getBody().get("id"));
    assertEquals(
        400,
        http.exchange(
                "/api/profile",
                HttpMethod.PUT,
                new HttpEntity<>(
                    Map.of(
                        "name",
                        "Bad",
                        "organization",
                        "",
                        "position",
                        "",
                        "phone",
                        "",
                        "avatar",
                        "https://external.invalid/avatar.png"),
                    owner),
                String.class)
            .getStatusCode()
            .value());
    assertEquals(401, http.getForEntity("/api/profile", String.class).getStatusCode().value());
  }

  @Test
  void guestCanRenderButCannotPersistAndCreatesNoAccountsRecordsOrJobs() throws Exception {
    var tables = List.of("users", "templates", "schemas", "data_models", "resource_revisions");
    var before =
        tables.stream()
            .map(table -> jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class))
            .toList();
    long filesBefore;
    try (var files = Files.list(generationFiles.root())) {
      filesBefore = files.count();
    }
    var slide =
        Map.of(
            "id",
            "guest-page",
            "name",
            "Guest page",
            "width",
            400,
            "height",
            200,
            "elements",
            List.of(
                Map.of(
                    "id",
                    "label",
                    "type",
                    "text",
                    "x",
                    20,
                    "y",
                    20,
                    "width",
                    300,
                    "height",
                    80,
                    "fontSize",
                    20,
                    "text",
                    "Guest {{name}}")));
    var payload =
        Map.of(
            "inlineSlides",
            List.of(slide),
            "items",
            List.of(Map.of("inlineValues", Map.of("name", "Alice"))),
            "fields",
            List.of(Map.of("path", "name", "type", "string", "required", true)),
            "format",
            "svg",
            "packAs",
            "single");
    var result = http.postForEntity("/api/guest/generate/sync", payload, String.class);
    assertEquals(200, result.getStatusCode().value(), result.toString());
    assertTrue(result.getBody().contains("Alice"));
    assertNull(result.getHeaders().getFirst("X-Generation-History"));
    assertEquals("no-store", result.getHeaders().getFirst("Cache-Control"));
    var pdf =
        http.postForEntity(
            "/api/guest/export/pdf?mode=pdfa",
            Map.of(
                "slides",
                List.of(slide),
                "options",
                Map.of("embedImages", true, "pdfVersion", "pdf/a-2b")),
            byte[].class);
    assertEquals(200, pdf.getStatusCode().value());
    assertTrue(
        new String(pdf.getBody(), 0, 5, java.nio.charset.StandardCharsets.US_ASCII)
            .startsWith("%PDF"));
    assertEquals(
        401,
        http.postForEntity(
                "/api/templates",
                Map.of("name", "Must not save", "slides", List.of(slide), "bindings", List.of()),
                String.class)
            .getStatusCode()
            .value());
    assertEquals(
        before,
        tables.stream()
            .map(table -> jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class))
            .toList());
    try (var files = Files.list(generationFiles.root())) {
      assertEquals(filesBefore, files.count());
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void libraryRolesProtectedFoldersAndOptimisticRevisionAreEnforced() {
    HttpHeaders owner = registeredHeaders("library-owner"),
        editor = registeredHeaders("library-editor"),
        viewer = registeredHeaders("library-viewer"),
        stranger = registeredHeaders("library-stranger");
    var created =
        http.exchange(
            "/api/libraries",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("name", "Brand"), owner),
            Map.class);
    assertEquals(200, created.getStatusCode().value(), created.toString());
    String id = (String) created.getBody().get("id"), path = "/api/libraries/" + id;
    assertEquals(
        404,
        http.exchange(path, HttpMethod.GET, new HttpEntity<>(stranger), String.class)
            .getStatusCode()
            .value());
    assertEquals(
        200,
        http.exchange(
                path + "/members",
                HttpMethod.POST,
                new HttpEntity<>(
                    Map.of("email", "library-editor@example.test", "role", "editor"), owner),
                String.class)
            .getStatusCode()
            .value());
    assertEquals(
        200,
        http.exchange(
                path + "/members",
                HttpMethod.POST,
                new HttpEntity<>(
                    Map.of("email", "library-viewer@example.test", "role", "viewer"), owner),
                String.class)
            .getStatusCode()
            .value());
    var folder = Map.of("id", "folder", "name", "Brand", "kind", "folder", "locked", true);
    var asset =
        Map.of(
            "id",
            "stamp",
            "name",
            "Stamp",
            "kind",
            "stamp",
            "folderId",
            "folder",
            "payload",
            Map.of("text", "Original"));
    var symbol =
        Map.of(
            "id",
            "symbol",
            "name",
            "Logo",
            "kind",
            "symbol",
            "payload",
            Map.of(
                "id",
                "symbol",
                "name",
                "Logo",
                "prototype",
                Map.of("type", "image", "src", "data:image/png;base64,AA==")));
    var items = List.of(folder, asset, symbol);
    assertEquals(
        200,
        http.exchange(
                path,
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("revision", 2, "items", items), owner),
                String.class)
            .getStatusCode()
            .value());
    assertEquals(
        403,
        http.exchange(
                path,
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("revision", 3, "items", items), viewer),
                String.class)
            .getStatusCode()
            .value());
    assertEquals(
        403,
        http.exchange(
                path,
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("revision", 3, "items", List.of(folder)), editor),
                String.class)
            .getStatusCode()
            .value());
    assertEquals(
        409,
        http.exchange(
                path,
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("revision", 2, "items", items), owner),
                String.class)
            .getStatusCode()
            .value());
    assertEquals(
        200,
        http.exchange(
                path,
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("revision", 3, "items", items), editor),
                String.class)
            .getStatusCode()
            .value());
    var listed =
        http.exchange("/api/libraries", HttpMethod.GET, new HttpEntity<>(viewer), String.class);
    assertTrue(listed.getBody().contains(id));
    assertFalse(listed.getBody().contains("Original"));
    http.exchange(
        path + "/members",
        HttpMethod.POST,
        new HttpEntity<>(Map.of("email", "library-editor@example.test", "role", "remove"), owner),
        String.class);
    assertEquals(
        404,
        http.exchange(path, HttpMethod.GET, new HttpEntity<>(editor), String.class)
            .getStatusCode()
            .value());
  }

  private HttpHeaders registeredHeaders(String name) {
    var result =
        http.postForEntity(
            "/api/auth/register",
            Map.of("email", name + "@example.test", "password", "test-password-123", "name", name),
            Map.class);
    assertNotNull(result.getBody(), result.toString());
    var headers = new HttpHeaders();
    headers.setBearerAuth((String) result.getBody().get("accessToken"));
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  @org.springframework.boot.test.mock.mockito.MockBean ru.ticket.clientapi.TykClient tyk;

  @Test
  @SuppressWarnings("unchecked")
  void clientKeyCanReadOnlyItsOwnersDataAndIsRevokedEvenWhenGatewayIsDown() {
    org.mockito.Mockito.when(tyk.enabled()).thenReturn(true);
    var login =
        http.postForEntity(
            "/api/auth/register",
            Map.of(
                "email",
                "client-api-owner@example.test",
                "password",
                "test-password-123",
                "name",
                "Owner"),
            Map.class);
    var ownerHeaders = new HttpHeaders();
    ownerHeaders.setBearerAuth((String) login.getBody().get("accessToken"));
    ownerHeaders.setContentType(MediaType.APPLICATION_JSON);
    var issued =
        http.exchange(
            "/api/client-keys",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("name", "1C", "scopes", java.util.List.of("read"), "days", 30),
                ownerHeaders),
            Map.class);
    assertEquals(200, issued.getStatusCode().value(), issued.toString());
    String token = (String) issued.getBody().get("token");
    String keyId = (String) ((Map<?, ?>) issued.getBody().get("key")).get("id");
    var keyHeaders = new HttpHeaders();
    keyHeaders.set("X-API-Key", token);
    keyHeaders.setContentType(MediaType.APPLICATION_JSON);
    assertEquals(
        200,
        http.exchange("/api/templates", HttpMethod.GET, new HttpEntity<>(keyHeaders), String.class)
            .getStatusCode()
            .value());
    assertEquals(
        403,
        http.exchange(
                "/api/client-keys", HttpMethod.GET, new HttpEntity<>(keyHeaders), String.class)
            .getStatusCode()
            .value());
    assertEquals(
        403,
        http.exchange(
                "/api/templates",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(), keyHeaders),
                String.class)
            .getStatusCode()
            .value());
    var listed =
        http.exchange(
            "/api/client-keys", HttpMethod.GET, new HttpEntity<>(ownerHeaders), String.class);
    assertFalse(listed.getBody().contains(token));
    assertFalse(listed.getBody().contains("encryptedToken"));
    assertFalse(listed.getBody().contains("tokenHash"));
    org.mockito.Mockito.doThrow(new RuntimeException("gateway unavailable"))
        .when(tyk)
        .revoke(token);
    var revoked =
        http.exchange(
            "/api/client-keys/" + keyId,
            HttpMethod.DELETE,
            new HttpEntity<>(ownerHeaders),
            Map.class);
    assertEquals(200, revoked.getStatusCode().value());
    assertEquals(false, revoked.getBody().get("gatewaySynchronized"));
    assertEquals(
        401,
        http.exchange("/api/templates", HttpMethod.GET, new HttpEntity<>(keyHeaders), String.class)
            .getStatusCode()
            .value());
  }

  @Test
  @SuppressWarnings("unchecked")
  void templateLivesInWorkspaceFolderAndGeneratesWithoutSchema() {
    var login =
        http.postForEntity(
            "/api/auth/register",
            Map.of(
                "email",
                "folder-owner@example.test",
                "password",
                "test-password-123",
                "name",
                "Owner"),
            Map.class);
    var headers = new HttpHeaders();
    headers.setBearerAuth((String) login.getBody().get("accessToken"));
    headers.setContentType(MediaType.APPLICATION_JSON);
    var folder =
        http.exchange(
            "/api/workspace/folders",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("name", "Certificates"), headers),
            Map.class);
    assertEquals(201, folder.getStatusCode().value());
    String folderId = (String) folder.getBody().get("id");
    var slide =
        Map.of(
            "id",
            "page",
            "name",
            "Certificate",
            "width",
            200,
            "height",
            100,
            "elements",
            List.of(
                Map.of(
                    "id",
                    "label",
                    "type",
                    "text",
                    "text",
                    "STATIC CERTIFICATE",
                    "x",
                    5,
                    "y",
                    5,
                    "width",
                    190,
                    "height",
                    50,
                    "fontSize",
                    16)));
    var payload =
        Map.of(
            "name",
            "Certificate",
            "folderId",
            folderId,
            "slides",
            List.of(slide),
            "bindings",
            List.of());
    var template =
        http.exchange(
            "/api/templates", HttpMethod.POST, new HttpEntity<>(payload, headers), Map.class);
    assertEquals(201, template.getStatusCode().value(), template.toString());
    String id = (String) template.getBody().get("id");
    assertNull(template.getBody().get("schemaId"));
    assertEquals(folderId, template.getBody().get("folderId"));
    var updated =
        http.exchange(
            "/api/templates/" + id,
            HttpMethod.PUT,
            new HttpEntity<>(
                Map.of("name", "Edited", "slides", List.of(slide), "bindings", List.of()), headers),
            Map.class);
    assertEquals(folderId, updated.getBody().get("folderId"), "Saving content must retain folder");
    var tree =
        http.exchange("/api/workspace/tree", HttpMethod.GET, new HttpEntity<>(headers), List.class);
    Map<String, Object> parent =
        (Map<String, Object>)
            tree.getBody().stream()
                .filter(node -> folderId.equals(((Map<?, ?>) node).get("id")))
                .findFirst()
                .orElseThrow();
    var children = (List<Map<String, Object>>) parent.get("children");
    assertEquals(1, children.size());
    assertEquals(id, children.get(0).get("id"));
    assertEquals("template", children.get(0).get("type"));
    var rendered =
        http.exchange(
            "/api/generate/sync",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of(
                    "templateId",
                    id,
                    "items",
                    List.of(Map.of("inlineValues", Map.of())),
                    "format",
                    "svg",
                    "packAs",
                    "single"),
                headers),
            String.class);
    assertEquals(200, rendered.getStatusCode().value(), rendered.toString());
    assertTrue(rendered.getBody().contains("STATIC CERTIFICATE"));
    String releaseId = rendered.getHeaders().getFirst("X-Generation-History");
    assertNotNull(releaseId);
    assertNotEquals("unavailable", releaseId);
    var savedRelease =
        http.exchange(
            "/api/generate/" + releaseId + "/result",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);
    assertEquals(
        rendered.getBody(), savedRelease.getBody(), "History downloads must preserve exact bytes");
    var versions =
        http.exchange(
            "/api/history/template/" + id, HttpMethod.GET, new HttpEntity<>(headers), List.class);
    assertEquals(2, versions.getBody().size(), "Create and content update are separate versions");
    String revision = (String) ((Map<?, ?>) versions.getBody().get(1)).get("id");
    var restored =
        http.exchange(
            "/api/history/template/" + id + "/" + revision + "/copy",
            HttpMethod.POST,
            new HttpEntity<>(headers),
            Map.class);
    assertEquals(200, restored.getStatusCode().value());
    assertNotEquals(id, restored.getBody().get("id"));
    var outsider =
        http.postForEntity(
            "/api/auth/register",
            Map.of(
                "email",
                "folder-outsider@example.test",
                "password",
                "test-password-123",
                "name",
                "Other"),
            Map.class);
    var otherHeaders = new HttpHeaders();
    otherHeaders.setBearerAuth((String) outsider.getBody().get("accessToken"));
    otherHeaders.setContentType(MediaType.APPLICATION_JSON);
    assertEquals(
        404,
        http.exchange(
                "/api/history/template/" + id + "/" + revision + "/copy",
                HttpMethod.POST,
                new HttpEntity<>(otherHeaders),
                String.class)
            .getStatusCode()
            .value());
    assertEquals(
        403,
        http.exchange(
                "/api/generate/" + releaseId + "/result",
                HttpMethod.GET,
                new HttpEntity<>(otherHeaders),
                String.class)
            .getStatusCode()
            .value());
    var denied =
        http.exchange(
            "/api/templates", HttpMethod.POST, new HttpEntity<>(payload, otherHeaders), Map.class);
    assertEquals(404, denied.getStatusCode().value(), "Cannot create in another user's folder");
    var otherTree =
        http.exchange(
            "/api/workspace/tree", HttpMethod.GET, new HttpEntity<>(otherHeaders), List.class);
    assertTrue(otherTree.getBody().isEmpty());
    var missingFolder =
        http.exchange(
            "/api/templates/" + id + "/parent",
            HttpMethod.PUT,
            new HttpEntity<>(Map.of("parentId", "missing"), headers),
            Map.class);
    assertEquals(404, missingFolder.getStatusCode().value());
    http.exchange(
        "/api/workspace/folders/" + folderId,
        HttpMethod.DELETE,
        new HttpEntity<>(headers),
        Void.class);
    var removed =
        http.exchange("/api/templates/" + id, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    assertEquals(
        404, removed.getStatusCode().value(), "Folder deletion must not leave orphaned templates");
  }

  @Test
  void batchPdfFillsEachTableFromItsSelectedModel() throws Exception {
    var headers = registeredHeaders("table-batch-owner");
    var fields = List.of(Map.of("path", "items", "type", "array", "required", true));
    var schema = http.postForEntity("/api/schemas", new HttpEntity<>(
        Map.of("name", "Orders", "fields", fields), headers), Map.class);
    assertTrue(schema.getStatusCode().is2xxSuccessful(), schema.toString());
    var schemaId = schema.getBody().get("id");
    var items = new ArrayList<Map<String, Object>>();
    for (String label : List.of("First", "Second")) {
      // The source name also exists in these saved models. An explicit remap must take precedence,
      // including when the new array is shorter or a column is absent.
      var templateRows = List.of(Map.of("name", "STALE", "price", 999), Map.of("name", "STALE"));
      var rows = label.equals("First")
          ? List.of(Map.of("name", "Alpha", "price", 125), Map.of("name", "Beta", "price", 250),
              Map.of("name", "Delta", "price", 375), Map.of("name", "Epsilon", "price", 500))
          : List.of(Map.of("name", "Gamma"));
      var model = http.postForEntity("/api/data-models", new HttpEntity<>(Map.of(
          "schemaId", schemaId, "name", label, "values", Map.of("items", rows, "sample", templateRows)),
          headers), Map.class);
      assertTrue(model.getStatusCode().is2xxSuccessful(), model.toString());
      items.add(Map.of("dataModelId", model.getBody().get("id")));
    }
    var table = Map.of("type", "table", "id", "lines", "rows", 2, "cols", 2, "width", 400,
        "height", 100, "data", List.of(List.of("Item", "Price"),
            List.of("{{sample[0].name}}", "{{sample[0].price}}")));
    var slide = Map.of("id", "page", "width", 500, "height", 300, "elements", List.of(table));
    var binding = Map.of("elementId", "lines", "placeholder", "sample", "schemaPath", "items",
        "source", "manual", "expectedType", "array");
    var response = http.postForEntity("/api/generate/sync", new HttpEntity<>(Map.of("schemaId", schemaId,
        "inlineSlides", List.of(slide), "bindings", List.of(binding), "items", items,
        "format", "pdf", "packAs", "multipage-pdf"), headers), byte[].class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    try (var pdf = org.apache.pdfbox.Loader.loadPDF(response.getBody())) {
      assertEquals(2, pdf.getNumberOfPages());
      var stripper = new org.apache.pdfbox.text.PDFTextStripper();
      stripper.setEndPage(1);
      var first = stripper.getText(pdf);
      org.assertj.core.api.Assertions.assertThat(first).contains(
          "Item", "Price", "Alpha", "125", "Beta", "250", "Delta", "375", "Epsilon", "500")
          .doesNotContain("Gamma", "STALE", "999", "{{");
      stripper.setStartPage(2);
      stripper.setEndPage(2);
      org.assertj.core.api.Assertions.assertThat(stripper.getText(pdf)).contains("Gamma")
          .doesNotContain("Alpha", "Beta", "Delta", "Epsilon", "125", "250", "375", "500", "STALE", "999", "{{");
    }
  }

  @Test
  void generatedPdfaKeepsTheWordGapInABoundCyrillicHeading() throws Exception {
    var heading = Map.ofEntries(Map.entry("type", "text"), Map.entry("id", "heading"),
        Map.entry("x", 24), Map.entry("y", 24), Map.entry("width", 345), Map.entry("height", 128),
        Map.entry("fontSize", 36), Map.entry("fontFamily", "Arial"), Map.entry("text", "{{name}}"),
        Map.entry("html", "<p>{{name}}</p>"));
    var request = Map.of("inlineSlides", List.of(Map.of("id", "page", "width", 400, "height", 170,
        "elements", List.of(heading))), "items", List.of(Map.of("inlineValues",
        Map.of("name", "Уважаемый клиент, это КП для Вас 345"))), "format", "pdfa", "packAs", "single");
    var preview = http.postForEntity("/api/guest/generate/preview", request, String.class);
    assertEquals(HttpStatus.OK, preview.getStatusCode());
    var words = org.jsoup.Jsoup.parse(preview.getBody(), "", org.jsoup.parser.Parser.xmlParser()).select("text");
    assertEquals("Уважаемый", words.get(0).text());
    assertEquals("клиент,", words.get(2).text());
    assertEquals(28.8, Double.parseDouble(words.get(0).attr("y")), 0.001);
    double firstWordEnd = Double.parseDouble(words.get(0).attr("textLength"));
    double secondWordStart = Double.parseDouble(words.get(2).attr("x"));
    assertTrue(secondWordStart - firstWordEnd > 5);

    var response = http.postForEntity("/api/guest/generate/sync", request, byte[].class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    try (var pdf = org.apache.pdfbox.Loader.loadPDF(response.getBody())) {
      var image = new org.apache.pdfbox.rendering.PDFRenderer(pdf).renderImageWithDPI(0, 192);
      int gap = (int) Math.round((24 + (firstWordEnd + secondWordStart) / 2) * 2);
      for (int y = 42; y < 124; y++) {
        for (int x = gap - 2; x <= gap + 2; x++) {
          assertTrue((image.getRGB(x, y) & 0xff) > 200, "Text crossed the measured word gap");
        }
      }
    }
  }

  @DynamicPropertySource
  static void storage(DynamicPropertyRegistry properties) throws Exception {
    String path = Files.createTempDirectory("ticket-contract-").toString();
    properties.add("app.generation.storage-dir", () -> path);
  }

  @SuppressWarnings("unchecked")
  @Test
  void ownerCanCreateModelsTemplatesRenderAndPollJobsButCannotDeleteUsedSchema() throws Exception {
    var login =
        http.postForEntity(
            "/api/auth/register",
            Map.of(
                "email", "contract@example.com", "password", "test-password-123", "name", "Test"),
            Map.class);
    assertTrue(login.getStatusCode().is2xxSuccessful(), login.toString());
    var headers = new HttpHeaders();
    headers.setBearerAuth((String) login.getBody().get("accessToken"));
    headers.setContentType(MediaType.APPLICATION_JSON);
    var fields =
        List.of(
            Map.of("path", "name", "type", "string", "required", true),
            Map.of("path", "optional", "type", "string", "required", false));
    var schema =
        http.exchange(
            "/api/schemas",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("name", "Ticket", "fields", fields), headers),
            Map.class);
    assertTrue(schema.getStatusCode().is2xxSuccessful(), schema.toString());
    String schemaId = (String) schema.getBody().get("id");
    var values = new LinkedHashMap<String, Object>();
    values.put("name", "Alice");
    values.put("optional", null);
    var model =
        http.exchange(
            "/api/data-models",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("schemaId", schemaId, "name", "Alice", "values", values), headers),
            Map.class);
    assertTrue(model.getStatusCode().is2xxSuccessful(), model.toString());
    assertTrue(((Map<?, ?>) model.getBody().get("values")).containsKey("optional"));
    var reimport =
        http.exchange(
            "/api/data-models/import",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of(
                    "schemaId",
                    schemaId,
                    "items",
                    List.of(
                        Map.of(
                            "id", model.getBody().get("id"), "name", "Alice", "values", values))),
                headers),
            Map.class);
    assertEquals(1, reimport.getBody().get("imported"));
    var afterImport =
        http.exchange(
            "/api/data-models?schemaId=" + schemaId,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            List.class);
    assertEquals(1, afterImport.getBody().size(), "Repeated Excel imports update the original ID");
    var foreignImport =
        http.exchange(
            "/api/data-models/import",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of(
                    "schemaId",
                    schemaId,
                    "items",
                    List.of(
                        Map.of("id", "unknown-or-foreign-id", "name", "Alice", "values", values))),
                headers),
            Map.class);
    assertEquals(0, foreignImport.getBody().get("imported"));
    assertEquals(1, ((List<?>) foreignImport.getBody().get("issues")).size());
    var text =
        Map.of(
            "id",
            "text",
            "type",
            "text",
            "text",
            "Ticket {{name}}",
            "x",
            10,
            "y",
            10,
            "width",
            200,
            "height",
            80,
            "fontSize",
            16);
    var qr =
        Map.of(
            "id",
            "qr",
            "type",
            "qr",
            "text",
            "{{name}}",
            "x",
            10,
            "y",
            100,
            "width",
            100,
            "height",
            100);
    var slide =
        Map.of(
            "id",
            "slide",
            "name",
            "Ticket",
            "width",
            400,
            "height",
            300,
            "elements",
            List.of(text, qr));
    var template =
        http.exchange(
            "/api/templates",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of(
                    "schemaId",
                    schemaId,
                    "name",
                    "Ticket",
                    "slides",
                    List.of(slide),
                    "bindings",
                    List.of()),
                headers),
            Map.class);
    assertTrue(template.getStatusCode().is2xxSuccessful(), template.toString());
    String templateId = (String) template.getBody().get("id");
    var workflowDefinition =
        new ru.ticket.workflow.WorkflowDefinition(
            1,
            "contract-workflow",
            "Personal document",
            "Complete your details",
            templateId,
            List.of(
                new ru.ticket.workflow.WorkflowDefinition.Step(
                    "Details",
                    List.of(
                        new ru.ticket.workflow.WorkflowDefinition.Field(
                            "name", "Name", "text", true, List.of())))),
            "Ready to download");
    assertEquals(
        401,
        http.postForEntity(
                "/api/workflows",
                new ru.ticket.workflow.WorkflowController.Save(workflowDefinition, null),
                String.class)
            .getStatusCode()
            .value());
    var workflowDraft =
        http.exchange(
            "/api/workflows",
            HttpMethod.POST,
            new HttpEntity<>(
                new ru.ticket.workflow.WorkflowController.Save(workflowDefinition, null), headers),
            ru.ticket.workflow.WorkflowService.Managed.class);
    assertEquals(200, workflowDraft.getStatusCode().value(), workflowDraft.toString());
    assertEquals(
        404,
        http.getForEntity("/api/public/workflows/contract-workflow", String.class)
            .getStatusCode()
            .value());
    var workflowPublished =
        http.exchange(
            "/api/workflows/contract-workflow/publish",
            HttpMethod.POST,
            new HttpEntity<>(
                new ru.ticket.workflow.WorkflowController.Publish(
                    workflowDraft.getBody().revision(), false),
                headers),
            ru.ticket.workflow.WorkflowService.Managed.class);
    assertEquals(200, workflowPublished.getStatusCode().value(), workflowPublished.toString());
    var publicWorkflow =
        http.getForEntity(
            "/api/public/workflows/contract-workflow",
            ru.ticket.workflow.WorkflowService.PublicView.class);
    assertEquals(200, publicWorkflow.getStatusCode().value());
    var workflowPdf =
        http.postForEntity(
            "/api/public/workflows/contract-workflow/pdf",
            new ru.ticket.workflow.WorkflowController.Run(
                publicWorkflow.getBody().revision(), Map.of("name", "Workflow visitor")),
            byte[].class);
    assertEquals(
        200,
        workflowPdf.getStatusCode().value(),
        new String(workflowPdf.getBody(), java.nio.charset.StandardCharsets.UTF_8));
    try (var generated = org.apache.pdfbox.Loader.loadPDF(workflowPdf.getBody())) {
      assertTrue(
          new org.apache.pdfbox.text.PDFTextStripper()
              .getText(generated)
              .contains("Workflow visitor"));
    }
    var request =
        Map.of(
            "templateId",
            templateId,
            "items",
            List.of(Map.of("dataModelId", model.getBody().get("id"))),
            "format",
            "svg",
            "packAs",
            "single",
            "filenamePattern",
            "ticket-{{name}}");
    var rendered =
        http.exchange(
            "/api/generate/sync",
            HttpMethod.POST,
            new HttpEntity<>(request, headers),
            String.class);
    assertEquals(200, rendered.getStatusCode().value(), rendered.toString());
    assertTrue(rendered.getBody().contains("Alice"));
    assertTrue(rendered.getBody().contains("image/png;base64"));
    assertTrue(
        rendered
            .getHeaders()
            .getFirst(HttpHeaders.CONTENT_DISPOSITION)
            .contains("ticket-Alice.svg"));
    var used =
        http.exchange(
            "/api/schemas/" + schemaId, HttpMethod.DELETE, new HttpEntity<>(headers), Map.class);
    assertEquals(409, used.getStatusCode().value());
    var job =
        http.exchange(
            "/api/generate", HttpMethod.POST, new HttpEntity<>(request, headers), Map.class);
    assertEquals(202, job.getStatusCode().value());
    String jobId = (String) job.getBody().get("jobId");
    Map<String, Object> status = Map.of();
    for (int attempt = 0; attempt < 100; attempt++) {
      var response =
          http.exchange(
              "/api/generate/" + jobId, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
      assertEquals(200, response.getStatusCode().value(), response.toString());
      status = response.getBody();
      if (Set.of("done", "failed").contains(status.get("status"))) break;
      Thread.sleep(50);
    }
    assertEquals("done", status.get("status"), status.toString());
    var result =
        http.exchange(
            "/api/generate/" + jobId + "/result",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);
    assertEquals("image/svg+xml", result.getHeaders().getContentType().toString());
    assertTrue(result.getBody().contains("Alice"));
    assertEquals(
        401,
        http.getForEntity("/api/templates/" + templateId, String.class).getStatusCode().value());
  }
}
