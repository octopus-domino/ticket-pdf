package ru.ticket.routing.trip;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ru.ticket.routing.RoutingApplication;

@SpringBootTest(classes = RoutingApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:trip-routes;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.journeys.trip-planner-enabled=true",
        "app.mystic.refresh-on-start=false"
    })
class TripRoutesTest {
  private static final String PATH = "/api/routing/v1/trips";
  private static final WireMockServer documents = new WireMockServer(0);
  @Autowired TestRestTemplate http;

  @DynamicPropertySource
  static void documents(DynamicPropertyRegistry properties) {
    documents.start();
    properties.add("TRIPS_DOCUMENT_URL", documents::baseUrl);
    documents.stubFor(get("/api/auth/me").withHeader("Authorization", equalTo("Bearer owner"))
        .willReturn(okJson("{\"id\":\"owner\"}")));
    documents.stubFor(get("/api/auth/me").withHeader("Authorization", equalTo("Bearer other"))
        .willReturn(okJson("{\"id\":\"other\"}")));
    documents.stubFor(get("/api/auth/me").withHeader("Authorization", equalTo("Bearer walk-owner"))
        .willReturn(okJson("{\"id\":\"walk-owner\"}")));
    documents.stubFor(get("/api/trips/legacy/historical").withHeader("Authorization", equalTo("Bearer owner"))
        .willReturn(okJson("{\"id\":\"historical\",\"meta\":{\"mode\":\"WALK\"}}")));
  }

  @AfterAll
  static void stopDocuments() {
    documents.stop();
  }

  @Test
  void listsCreatesAndOpensTripsOnlyForTheirAccount() {
    assertThat(http.getForEntity(PATH, JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(request(PATH, HttpMethod.GET, "missing", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    var trip = """
        {"version":2,"id":"winter","title":"Winter route","startDate":"2027-01-01","endDate":"2027-01-02",
         "baseCurrency":"RUB","travelers":[],"cities":[],"days":[],"legs":[],"accommodations":[],"documents":[],
         "meta":{"mode":"TRIP","source":"MANUAL","legacyFields":{}}}
        """;
    var created = request(PATH, HttpMethod.POST, "owner", trip);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getHeaders().getCacheControl()).isEqualTo("no-store");
    assertThat(request(PATH, HttpMethod.POST, "owner", trip).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(request(PATH + "/winter", HttpMethod.GET, "owner", null).getBody()).isEqualTo(created.getBody());
    assertThat(request(PATH + "/winter", HttpMethod.GET, "other", null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(request(PATH, HttpMethod.GET, "other", null).getBody().path("items")).isEmpty();
    var items = request(PATH, HttpMethod.GET, "owner", null).getBody().path("items");
    assertThat(items).hasSize(1);
    assertThat(items.get(0).path("title").asText()).isEqualTo("Winter route");
    assertThat(items.get(0).has("travelers")).isFalse();
    var edited = request(PATH + "/winter", HttpMethod.PUT, "owner", trip.replace("Winter route", "Edited route"));
    assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(request(PATH, HttpMethod.GET, "owner", null).getBody().at("/items/0/title").asText())
        .isEqualTo("Edited route");
    assertThat(request(PATH + "/other-id", HttpMethod.PUT, "owner", trip).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(request(PATH, HttpMethod.POST, "owner", trip.replace("2027-01-02", "2027-01-01")).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(request(PATH, HttpMethod.POST, "owner", trip.replace("\"cities\":[]", "\"cities\":[null]")).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void roundTripsTheSelectedWalkAndItsAttributionThroughAccountStorage() {
    String image = "a".repeat(64);
    var payload = """
        {"version":2,"id":"walk-trip","title":"Walking itinerary","startDate":"2027-01-01","endDate":"2027-01-02",
         "travelers":[],"cities":[{"id":"city","name":"Berlin"}],"accommodations":[],"documents":[],
         "legs":[{"id":"main-train","transport":"TRAIN","note":"Keep this transfer"}],
         "days":[{"date":"2027-01-02","cityId":"city","segments":[
           {"id":"first","title":"Start","walkId":"walk-1","note":"First note","photoRefs":["%s"],"photoCredits":[
             {"stopId":"first","imageId":"%s","author":"Fixture Author","license":"CC BY 4.0",
              "licenseUrl":"https://creativecommons.org/licenses/by/4.0/","sourceUrl":"https://commons.wikimedia.org/wiki/File:Start.jpg"}]},
           {"id":"second","title":"Finish","walkId":"walk-1"}],
           "walkingSections":[{"fromSegmentId":"first","toSegmentId":"second","geometry":[
             {"longitude":13.40,"latitude":52.50},{"longitude":13.41,"latitude":52.51}]}]}],
         "meta":{"mode":"TRIP","source":"PRIVATE_JOURNEY"}}
        """.formatted(image, image);
    var created = request(PATH, HttpMethod.POST, "walk-owner", payload);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    var reopened = request(PATH + "/walk-trip", HttpMethod.GET, "walk-owner", null).getBody();
    assertThat(reopened).isEqualTo(created.getBody());
    assertThat(reopened.at("/days/0/segments/0/photoCredits/0/author").asText()).isEqualTo("Fixture Author");
    var edited = request(PATH + "/walk-trip", HttpMethod.PUT, "walk-owner", payload.replace("First note", "Edited note"));
    assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(request(PATH + "/walk-trip", HttpMethod.GET, "walk-owner", null).getBody()).isEqualTo(edited.getBody());
    assertThat(edited.getBody().path("legs")).isEqualTo(reopened.path("legs"));
  }

  @Test
  void opensAnOwnedLegacyPublicationThroughTheDocumentService() {
    var response = request(PATH + "/historical", HttpMethod.GET, "owner", null);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().path("id").asText()).isEqualTo("historical");
    assertThat(request(PATH + "/historical", HttpMethod.GET, "other", null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  private ResponseEntity<JsonNode> request(String path, HttpMethod method, String auth, String body) {
    var headers = new HttpHeaders();
    headers.setBearerAuth(auth);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
  }
}
