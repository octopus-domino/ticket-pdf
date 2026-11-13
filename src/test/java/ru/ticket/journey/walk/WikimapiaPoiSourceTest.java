package ru.ticket.journey.walk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.*;
import org.junit.jupiter.api.*;
import ru.ticket.journey.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WikimapiaPoiSourceTest {
  private final ObjectMapper mapper=new ObjectMapper();
  private WireMockServer server;
  @BeforeEach void start(){server=new WireMockServer(0);server.start();}
  @AfterEach void stop(){server.stop();}
  private WikimapiaPoiSourceImpl source(String key,int quota){return new WikimapiaPoiSourceImpl(new WikimapiaKeyProvider(key),new WikimapiaRateLimiter(quota,5000),new WalkRemote(mapper),mock(OsmPlaceService.class),server.baseUrl()+"/",100);}
  private WalkThemeProperties.Theme theme(){return new WalkThemeProperties.Theme(List.of("amenity=bar"),List.of("bar"),List.of("ghost"),false);}
  @Test void areaBoundsDistinguishDistrictsFromCourtyardsAndTolerateMalformedInput() {
    assertTrue(WikimapiaPoiSourceImpl.isLargeArea("59.90,30.20,59.95,30.30"));
    assertFalse(WikimapiaPoiSourceImpl.isLargeArea("59.9000,30.2000,59.9004,30.2005"));
    for(String box:List.of("", "bad", "59,no,60,30", "NaN,30,60,31", "91,30,92,31"))
      assertFalse(WikimapiaPoiSourceImpl.isLargeArea(box),box);
  }
  @Test void enrichmentRejectsLargeAreaEvenWithMatchingCategoryAndPhotos() {
    server.stubFor(get(anyUrl()).willReturn(okJson("{\"id\":1,\"title\":\"Historic district\",\"box\":\"55.70,37.60,55.80,37.70\",\"categories\":[\"bar\"],\"photos_count\":2,\"location\":{\"lon\":37.62,\"lat\":55.75}}")));
    var source=source("test-only-key",20);
    assertNull(source.enrich(1,"en"));
    assertNull(source.enrich(1,"en"));
    server.verify(1,getRequestedFor(urlPathEqualTo("/")));
  }
  @Test void boxIsUncategorisedAndPaginatesWhileKeepingCommunityCandidates() throws Exception {
    var payload=mapper.createObjectNode();var folder=payload.putArray("folder");
    for(int i=1;i<=100;i++){
      var point=folder.addObject().put("id",i).put("name","Place "+i);point.putObject("location").put("lon",37.62).put("lat",55.75);
      if(i<90)point.putArray("categories").addObject().put("title","school");
      else if(i<95)point.putArray("categories").addObject().put("title","bar");
    }
    server.stubFor(get(urlPathEqualTo("/")).withQueryParam("page",equalTo("1")).willReturn(okJson(payload.toString())));
    server.stubFor(get(urlPathEqualTo("/")).withQueryParam("page",equalTo("2")).willReturn(okJson("{\"folder\":[]}")));
    var result=source("test-only-key",20).fetchInBox(37.61,55.74,37.63,55.76,theme(),"ru");
    assertEquals(11,result.size());assertTrue(result.stream().anyMatch(point->point.categories().isEmpty()));
    server.verify(2,getRequestedFor(urlPathEqualTo("/")).withQueryParam("category",absent()));
  }
  @Test void detailsUnderstandRealTitleTagsAndPhotoFieldsAndRespectQuota() {
    server.stubFor(get(urlPathEqualTo("/")).withQueryParam("function",equalTo("place.getbyid"))
        .willReturn(okJson("{\"id\":1,\"title\":\"Old courtyard\",\"description\":\"<p>A ghost legend about this old courtyard.</p>\",\"tags\":[{\"id\":12,\"title\":\"historic\"}],\"location\":{\"lon\":37.62,\"lat\":55.75},\"photos\":[{\"big_url\":\"https://photos.wikimapia.org/p/01_big.jpg\",\"thumbnail_url\":\"https://photos.wikimapia.org/p/01.jpg\",\"user_name\":\"Photographer\"}]}")));
    server.stubFor(get(urlPathEqualTo("/")).withQueryParam("function",equalTo("box")).willReturn(okJson("{\"folder\":[]}")));
    var source=source("test-only-key",2);var detail=source.enrich(1,"en");
    assertNotNull(detail);assertFalse(detail.description().contains("<p>"));assertEquals("historic",detail.categoryNames().get(0));assertEquals("unknown",detail.photos().get(0).license());
    assertNull(source.enrich(2,"en"));assertEquals(List.of(),source.fetchInBox(37.61,55.74,37.63,55.76,theme(),"en"));
    server.verify(1,getRequestedFor(urlPathEqualTo("/")).withQueryParam("function",equalTo("box")));
  }
  @Test void missingKeyAndProviderOutageAreOptional(){
    var disabled=source("",20);assertFalse(disabled.enabled());assertTrue(disabled.fetchInBox(37.61,55.74,37.63,55.76,theme(),"en").isEmpty());
    server.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(503)));
    assertTrue(source("test-only-key",20).fetchInBox(37.61,55.74,37.63,55.76,theme(),"en").isEmpty());
  }
}
