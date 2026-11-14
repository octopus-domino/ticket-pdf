package ru.ticket.journey;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.locationtech.jts.io.WKTReader;
import ru.ticket.journey.walk.WalkThemeService;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:local-places;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop",
    "jwt.secret=local-places-fixture-only-secret-01234567890123456789","app.rate-limit.enabled=false",
    "app.mcp.osm.enabled=false","app.mcp.commons.enabled=false",
    "spring.security.oauth2.client.registration.google.client-id=test","spring.security.oauth2.client.registration.google.client-secret=test",
    "SMTP_HOST=localhost","SMTP_USER=test@example.invalid","EMAIL_FROM=test@example.invalid"})
class LocalPlacesIntegrationTest {
  @Autowired MysticPlaceRepository places;
  @Autowired MysticPlaceImporter importer;
  @Autowired WalkThemeService walks;
  @Autowired BuildingHistoryService history;
  @Autowired BuildingHistoryRepository buildings;
  @Autowired TestRestTemplate http;
  @org.springframework.boot.test.mock.mockito.MockBean ru.ticket.journey.walk.OsmPoiSource osm;
  @org.springframework.boot.test.mock.mockito.MockBean ru.ticket.journey.walk.PhotonPoiSource photon;
  @org.springframework.boot.test.mock.mockito.MockBean ru.ticket.journey.walk.WikimapiaPoiSource wikimapia;

  @Test void startupImportsTheRealCatalogAndMysticSearchIsLocal(){
    assertTrue(places.count()>150);
    var found=http.getForEntity("/api/journeys/routes/places/themed?longitude=30.315&latitude=59.938&theme=mystic&language=ru&maxStops=7",PlaceRecommendationsController.Result.class);
    assertEquals(200,found.getStatusCode().value());assertEquals("mystic",found.getBody().source());
    assertEquals(7,found.getBody().places().size());
    assertTrue(found.getBody().places().stream().allMatch(p->p.source().equals("mystic")&&p.url().startsWith("https://")&&!p.description().isBlank()));
    assertEquals(7,found.getBody().places().stream().map(p->p.longitude()+":"+p.latitude()).distinct().count());
    assertEquals(400,http.getForEntity("/api/journeys/routes/places/themed?longitude=NaN&latitude=59.938&theme=mystic",String.class).getStatusCode().value());
    org.mockito.Mockito.verifyNoInteractions(osm,photon,wikimapia);
  }
  @Test void csvImportIsIdempotentSkipsBadRowsAndSupportsQuotedNewlines() throws Exception {
    String csv="\ufeffname,region,city,latitude,longitude,category,description,source_url\n"
        +"\"CSV fixture\",r,c,59.938,30.315,urban_legend,\"Line one, with comma\nLine two\",https://example.org/story\n"
        +"bad,r,c,NaN,30,urban_legend,bad,https://example.org\n"
        +"bad-url,r,c,59,30,urban_legend,bad,/relative\n";
    long before=places.count();
    assertEquals(1,importer.importFromCsv(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))));
    assertEquals(before+1,places.count());
    assertEquals(1,importer.importFromCsv(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))));
    assertEquals(before+1,places.count());
    assertTrue(places.findByNameAndLatitudeAndLongitude("CSV fixture",59.938,30.315).orElseThrow().getDescription().contains("\n"));
    places.delete(places.findByNameAndLatitudeAndLongitude("CSV fixture",59.938,30.315).orElseThrow());
  }
  @Test void anyYearIncludesModernAndUndatedNamedHousesAndPreservesTheirInformation() throws Exception {
    buildings.deleteAll();
    var geometry=new WKTReader().read("POLYGON((30.314 59.937,30.316 59.937,30.316 59.939,30.314 59.939,30.314 59.937))");geometry.setSRID(4326);
    for(int i=0;i<2;i++){
      var row=new BuildingHistory();row.setSource("HOITH");row.setOsmId("all-years-"+i);row.setAddress("Fixture address");
      row.setName("Named building "+i);row.setConstructionYear(i==0?2005:null);row.setConstructionPeriod(i==0?"2004–2005":"");row.setGeometry(geometry);history.upsertHoith(List.of(row));
    }
    assertEquals(2,history.nearby(30.315,59.938,7,1.5,"named building",0).size());
    assertTrue(history.nearby(30.315,59.938,7,1.5,"named building",1945).isEmpty());
    var found=http.getForEntity("/api/journeys/buildings/nearby?lon=30.315&lat=59.938&beforeYear=0",BuildingHistoryService.Info[].class);
    assertEquals(200,found.getStatusCode().value());assertEquals(2,found.getBody().length);
    assertTrue(Arrays.stream(found.getBody()).anyMatch(p->"2004–2005".equals(p.constructionPeriod())));
  }
  @Test void buildingLookupUpsertBoundaryPriorityAndNearbySearch() throws Exception {
    buildings.deleteAll();
    var row=new BuildingHistory();row.setOsmId("hoith:fixture:1");row.setAddress("Historic fixture");row.setConstructionYear(1850);
    row.setArchitect("Fixture Architect");row.setStyle("Classicism");row.setSource("HOITH");row.setSourceUrl("https://example.org/history");
    var polygon=new WKTReader().read("POLYGON((30.314 59.937,30.316 59.937,30.316 59.939,30.314 59.939,30.314 59.937))");polygon.setSRID(4326);row.setGeometry(polygon);
    assertEquals(1,history.upsertHoith(List.of(row)));row.setConstructionYear(1851);history.upsertHoith(List.of(row));
    assertEquals(1,buildings.count());assertEquals(1851,history.findByPoint(30.315,59.938).orElseThrow().getConstructionYear());
    assertTrue(history.findByPoint(30.314,59.937).isPresent());
    assertTrue(history.findByPoint(30.32,59.94).isEmpty());
    var response=http.getForEntity("/api/journeys/buildings?lon=30.315&lat=59.938",BuildingHistoryService.Info.class);
    assertEquals(200,response.getStatusCode().value());assertEquals("Fixture Architect",response.getBody().architect());
    assertEquals(1,history.nearby(30.315,59.938,7,1.5,"fixture",1900).size());
    assertTrue(history.nearby(30.315,59.938,7,1.5,"not present",1900).isEmpty());
    var candidate=new WalkThemeService.WalkCandidate("osm","node-1","Pub",30.315,59.938,"","UNKNOWN",null,true,"Original",List.of(),1,"","");
    var enriched=history.enrich(List.of(candidate),"ru").get(0);assertTrue(enriched.description().contains("1851"));assertTrue(enriched.description().contains("Original"));
    var passport=new BuildingHistory();passport.setSource("SPB_PASSPORT");passport.setGeometry(polygon);passport.setConstructionYear(1849);buildings.save(passport);
    assertEquals("SPB_PASSPORT",history.findByPoint(30.315,59.938).orElseThrow().getSource());
  }
}
