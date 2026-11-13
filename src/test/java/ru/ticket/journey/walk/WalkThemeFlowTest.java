package ru.ticket.journey.walk;

import com.fasterxml.jackson.databind.*;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ru.ticket.journey.*;
import ru.ticket.mcp.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
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
  @Test void themedDiscoveryEnrichmentSnapshotAndPdfUseOneNativeFlow() throws Exception {
    var found=http.getForEntity("/api/journeys/routes/places/themed?theme=cultural&longitude=37.62&latitude=55.75&language=en&visitTime=2026-11-13T20:00",PlaceRecommendationsController.Result.class);
    assertEquals(200,found.getStatusCode().value());var places=found.getBody().places();assertEquals(2,places.size());
    assertTrue(places.stream().anyMatch(place->"wikimapia".equals(place.source())&&Boolean.FALSE.equals(place.inOsm())&&place.description().contains("ghost legend")));
    assertTrue(places.stream().anyMatch(place->"CLOSED".equals(place.visitStatus())));
    var stops=places.stream().map(place->new JourneyRouteService.Stop(place.id(),place.label(),place.longitude(),place.latitude(),"Europe/Moscow",0,
        place.description()==null?"":place.description(),null,place.meta())).toList();
    var attributed=http.postForEntity("/api/journeys/routes/places/themed/enrich",stops,JourneyRouteService.Photo[].class);
    assertEquals(200,attributed.getStatusCode().value());assertEquals(2,attributed.getBody().length);
    var map=http.postForEntity("/api/journeys/media",Map.of("image",image()),JourneyMediaService.Asset.class).getBody();
    var snapshot=new JourneyRouteService.Snapshot(stops,List.of(new JourneyRouteService.Leg("foot",60,null,null,null,null,
        List.of(List.of(stops.get(0).longitude(),stops.get(0).latitude()),List.of(stops.get(1).longitude(),stops.get(1).latitude())),100.0)),"2026-11-13T20:00",
        List.of(new JourneyRouteService.Row("stop",stops.get(0).label(),"2026-11-13T17:00:00Z","2026-11-13T17:00:00Z","Europe/Moscow"),
            new JourneyRouteService.Row("foot","Walk","2026-11-13T17:00:00Z","2026-11-13T17:01:00Z","Europe/Moscow"),
            new JourneyRouteService.Row("stop",stops.get(1).label(),"2026-11-13T17:01:00Z","2026-11-13T17:01:00Z","Europe/Moscow")),map.id(),List.of(attributed.getBody()));
    var saved=http.postForEntity("/api/journeys/routes",snapshot,JourneyRouteService.Saved.class);assertEquals(200,saved.getStatusCode().value(),saved.toString());
    var restored=http.getForObject("/api/journeys/routes/"+saved.getBody().id(),JourneyRouteService.Snapshot.class);assertEquals(snapshot,restored);
    var created=http.postForEntity("/api/journeys",new JourneyService.Input(JourneyService.Kind.TOUR,"Themed walk fixture",Map.of("route",saved.getBody().id(),"locale","en"),List.of()),JourneyService.Created[].class);
    assertEquals(200,created.getStatusCode().value());String id=created.getBody()[0].document().id();
    var pdf=http.getForEntity("/api/journeys/"+id+"/pdf",byte[].class);assertEquals(200,pdf.getStatusCode().value());
    try(var document=Loader.loadPDF(pdf.getBody())){
      String text=new PDFTextStripper().getText(document);assertTrue(text.contains("Community place"));assertTrue(text.contains("Closed according to OSM"));assertTrue(text.contains("Opening hours not provided"));
    }
  }
  @Test void wikimapiaHttpOutageDoesNotBreakOsmOnlyDiscovery(){
    UPSTREAM.stubFor(get(urlPathEqualTo("/")).atPriority(1).willReturn(aResponse().withStatus(503)));
    var found=http.getForEntity("/api/journeys/routes/places/themed?theme=cultural&longitude=37.622&latitude=55.75&language=en&visitTime=2026-11-13T19:00",PlaceRecommendationsController.Result.class);
    assertEquals(200,found.getStatusCode().value());assertEquals(1,found.getBody().places().size());assertEquals("osm",found.getBody().places().get(0).source());
  }
  private static String image() throws Exception {
    var raster=new BufferedImage(32,32,BufferedImage.TYPE_INT_RGB);var bytes=new ByteArrayOutputStream();ImageIO.write(raster,"png",bytes);raster.flush();
    return "data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes.toByteArray());
  }
}
