package ru.ticket.walks.infrastructure.poi;

import com.fasterxml.jackson.databind.*;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ru.ticket.walks.web.PlaceRecommendationsController;
import ru.ticket.walks.domain.model.WalkPhoto;
import ru.ticket.walks.infrastructure.mcp.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = ru.ticket.routing.RoutingApplication.class, webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:themed-walk;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop",
    "jwt.secret=themed-walk-fixture-only-secret-01234567890123456789","app.rate-limit.enabled=false",
    "app.mcp.osm.enabled=false","app.mcp.commons.enabled=false",
    "app.journeys.walk-themes.cultural.tags[0]=historic=ruins",
    "app.journeys.walk-themes.cultural.description-keywords[0]=ghost",
    "spring.security.oauth2.client.registration.google.client-id=test","spring.security.oauth2.client.registration.google.client-secret=test",
    "SMTP_HOST=localhost","SMTP_USER=test@example.invalid","EMAIL_FROM=test@example.invalid"})
class WalkThemeFlowTest {
  private static final WireMockServer UPSTREAM=new WireMockServer(0);
  static { UPSTREAM.start(); }
  @DynamicPropertySource static void configuration(DynamicPropertyRegistry registry){
    registry.add("walks.print-media-url", UPSTREAM::baseUrl);
    registry.add("app.journeys.wikimapia-key",()->"fixture-key-not-a-production-secret");
    registry.add("app.journeys.wikimapia-url",()->UPSTREAM.baseUrl()+"/");
    registry.add("app.journeys.overpass-url",()->UPSTREAM.baseUrl()+"/overpass");
  }
  @Autowired TestRestTemplate http;
  @Autowired ObjectMapper mapper;
  @MockBean OsmMcpClient osm;
  @MockBean CommonsMcpClient commons;
  @MockBean WikidataPopularity popularity;
  @SpyBean PhotoEnricher photos;
  @AfterAll static void stop(){UPSTREAM.stop();}
  @BeforeEach void providers() throws Exception {
    UPSTREAM.resetAll();
    UPSTREAM.stubFor(post(urlEqualTo("/api/journeys/media"))
        .willReturn(okJson(mapper.writeValueAsString(Map.of("id", "a".repeat(64))))));
    UPSTREAM.stubFor(get(urlPathEqualTo("/")).withQueryParam("function",equalTo("box")).willReturn(okJson("{\"folder\":[{\"id\":123,\"name\":\"Legend courtyard\",\"location\":{\"lon\":37.621,\"lat\":55.75}}]}")));
    UPSTREAM.stubFor(get(urlPathEqualTo("/")).withQueryParam("function",equalTo("place.getbyid")).willReturn(okJson("{\"id\":123,\"title\":\"Legend courtyard\",\"description\":\"A ghost legend remembered by the local community.\",\"location\":{\"lon\":37.621,\"lat\":55.75},\"tags\":[{\"title\":\"historic\"}]}")));
    UPSTREAM.stubFor(post(urlEqualTo("/overpass")).willReturn(okJson("{\"elements\":[]}")));
    when(osm.enabled()).thenReturn(true);
    var point=new OsmMcpClient.Poi("node/456","Closed ruins",37.62,55.75,Map.of("historic","ruins","opening_hours","Mo-Fr 09:00-18:00","wikidata","Q1"));
    when(osm.findNearby(anyDouble(),anyDouble(),anyInt(),anyList(),anyInt())).thenReturn(List.of(point));
    when(osm.poiDetails("node/456")).thenReturn(new OsmMcpClient.PoiDetails(point,"Mo-Fr 09:00-18:00","Q1",2));
    when(popularity.counts(anyCollection())).thenReturn(Map.of("Q1",2));
    when(commons.enabled()).thenReturn(true);
    when(commons.searchByName(anyString(),anyDouble(),anyDouble(),anyInt(),anyInt())).thenReturn(List.of(new CommonsMcpClient.CommonsImage(
        "https://upload.wikimedia.org/fixture.jpg","https://commons.wikimedia.org/wiki/File:Fixture.jpg","Fixture photographer","CC BY 4.0","https://creativecommons.org/licenses/by/4.0/")));
    doReturn(image()).when(photos).download(anyString());
  }
  @Test void themedDiscoveryAttachesAttributedPhotographsFromDocumentStorage() throws Exception {
    var found=http.getForEntity("/api/walks/v1/places/themed?theme=cultural&longitude=37.62&latitude=55.75&language=en&visitTime=2026-11-13T20:00",PlaceRecommendationsController.Result.class);
    assertEquals(200,found.getStatusCode().value());var places=found.getBody().places();assertEquals(2,places.size());
    assertTrue(places.stream().anyMatch(place->"wikimapia".equals(place.source())&&Boolean.FALSE.equals(place.inOsm())&&place.description().contains("ghost legend")));
    assertTrue(places.stream().anyMatch(place->"CLOSED".equals(place.visitStatus())));
    var stops = places.stream().map(place -> new WalkPhoto.Stop(place.id(), place.label(),
        place.longitude(), place.latitude(), place.meta())).toList();
    var attributed = http.postForEntity("/api/walks/v1/places/themed/enrich", stops, WalkPhoto[].class);
    assertEquals(200, attributed.getStatusCode().value());
    assertEquals(2, attributed.getBody().length, () -> UPSTREAM.getAllServeEvents().stream()
        .map(event -> event.getRequest().getUrl() + " " + event.getResponse().getStatus()).toList().toString());
    assertTrue(Arrays.stream(attributed.getBody()).allMatch(photo -> "a".repeat(64).equals(photo.imageId())
        && !photo.licenseUrl().isBlank() && !photo.author().isBlank()));
    UPSTREAM.verify(2, postRequestedFor(urlEqualTo("/api/journeys/media"))
        .withRequestBody(matchingJsonPath("$.image", matching("data:image/png;base64,.*"))));
  }
  @Test void wikimapiaHttpOutageDoesNotBreakOsmOnlyDiscovery(){
    UPSTREAM.stubFor(get(urlPathEqualTo("/")).atPriority(1).willReturn(aResponse().withStatus(503)));
    var found=http.getForEntity("/api/walks/v1/places/themed?theme=cultural&longitude=37.622&latitude=55.75&language=en&visitTime=2026-11-13T19:00",PlaceRecommendationsController.Result.class);
    assertEquals(200,found.getStatusCode().value());assertEquals(1,found.getBody().places().size());assertEquals("osm",found.getBody().places().get(0).source());
  }
  private static String image() throws Exception {
    var raster=new BufferedImage(32,32,BufferedImage.TYPE_INT_RGB);var bytes=new ByteArrayOutputStream();ImageIO.write(raster,"png",bytes);raster.flush();
    return "data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes.toByteArray());
  }
}
