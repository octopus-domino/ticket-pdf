package ru.ticket.journey.walk;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import ru.ticket.journey.OpeningHoursEvaluator;
import ru.ticket.mcp.OsmMcpClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WalkThemeServiceTest {
  private final OsmPoiSource osm=mock(OsmPoiSource.class);
  private final WikimapiaPoiSource wiki=mock(WikimapiaPoiSource.class);
  private final WikidataPopularity popularity=mock(WikidataPopularity.class);
  private final WalkThemeProperties themes=new WalkThemeProperties();
  private WalkThemeService service(){
    themes.setWalkThemes(Map.of("bars",new WalkThemeProperties.Theme(List.of("amenity=bar","amenity=pub"),List.of("pub"),List.of(),true)));
    return new WalkThemeService(themes,osm,mock(PhotonPoiSource.class),wiki,new OpeningHoursEvaluator(new ObjectMapper()),popularity,25,20);
  }
  private WalkThemeService.WalkRequest request(){return new WalkThemeService.WalkRequest(37.62,55.75,"bars",LocalDateTime.parse("2026-11-13T20:00"),"en",7,1.5);}
  @Test void historicCategoryAloneDoesNotMakeAMysticStopButALegendDoes() throws Exception {
    var historic=new WikimapiaPoiSource.WikimapiaPoi(123,"Historic centre",37.621,55.75,List.of("historic"),true);
    var courtyard=new WikimapiaPoiSource.WikimapiaPoi(124,"Old courtyard",37.622,55.75,List.of("historic"),false);
    themes.setWalkThemes(Map.of("mystic",new WalkThemeProperties.Theme(List.of("historic=ruins"),List.of("ghost","abandoned"),List.of("ghost","legend"),false)));
    when(wiki.enabled()).thenReturn(true);
    when(wiki.fetchInBox(anyDouble(),anyDouble(),anyDouble(),anyDouble(),any(),anyString())).thenReturn(List.of(historic,courtyard));
    when(wiki.enrich(123,"en")).thenReturn(new WikimapiaPoiSource.WikimapiaPoiDetails(historic,"The historic administrative centre of the city.",List.of("historic"),List.of()));
    when(wiki.enrich(124,"en")).thenReturn(new WikimapiaPoiSource.WikimapiaPoiDetails(courtyard,"A ghost legend about this particular courtyard.",List.of("historic"),List.of()));
    when(osm.search(anyDouble(),anyDouble(),anyInt(),any(),anyString())).thenReturn(List.of());
    when(wiki.crossCheckOsm(anyList())).thenReturn(Set.of());
    var service=new WalkThemeService(themes,osm,mock(PhotonPoiSource.class),wiki,new OpeningHoursEvaluator(new ObjectMapper()),popularity,25,20);
    try{
      var result=service.findCandidates(new WalkThemeService.WalkRequest(37.62,55.75,"mystic",null,"en",7,1.5));
      assertEquals(1,result.size());assertEquals("wikimapia-124",result.get(0).id());
    }finally{service.close();}
  }
  @Test void rejectedDetailCannotReenterThroughBoxCategories() throws Exception {
    var point=new WikimapiaPoiSource.WikimapiaPoi(123,"Large district",37.621,55.75,List.of("pub"),true);
    when(wiki.enabled()).thenReturn(true);
    when(wiki.fetchInBox(anyDouble(),anyDouble(),anyDouble(),anyDouble(),any(),anyString())).thenReturn(List.of(point));
    when(osm.search(anyDouble(),anyDouble(),anyInt(),any(),anyString())).thenReturn(List.of());
    var service=service();try{assertTrue(service.findCandidates(request()).isEmpty());}finally{service.close();}
  }
  @Test void retainsClosedPlacesAndGivesCommunityOnlyPlacesTheirBonus() throws Exception {
    var point=new WikimapiaPoiSource.WikimapiaPoi(123,"Community pub",37.621,55.75,List.of("pub"),false);
    when(wiki.enabled()).thenReturn(true);when(wiki.fetchInBox(anyDouble(),anyDouble(),anyDouble(),anyDouble(),any(),anyString())).thenReturn(List.of(point));
    when(wiki.enrich(123,"en")).thenReturn(new WikimapiaPoiSource.WikimapiaPoiDetails(point,"A historic community meeting place.",List.of("pub"),List.of()));when(wiki.crossCheckOsm(anyList())).thenReturn(Set.of());
    when(osm.search(anyDouble(),anyDouble(),anyInt(),any(),anyString())).thenReturn(List.of(new OsmMcpClient.Poi("node/1","Closed pub",37.62,55.75,Map.of("amenity","pub","opening_hours","Mo-Fr 09:00-18:00"))));
    var service=service();try{
      var result=service.findCandidates(request());assertEquals(2,result.size());assertEquals("wikimapia",result.get(0).source());assertEquals(1.5,result.get(0).score(),.0001);assertFalse(result.get(0).inOsm());
      assertEquals("CLOSED",result.get(1).visitStatus());assertEquals(1.1,result.get(1).score(),.0001);
      service.findCandidates(request());verify(osm,times(1)).search(anyDouble(),anyDouble(),anyInt(),any(),anyString());
    }finally{service.close();}
  }
  @Test void collisionPrefersWikimapiaDescriptionButRetainsOsmHours() throws Exception {
    var point=new WikimapiaPoiSource.WikimapiaPoi(123,"Same pub",37.62,55.75,List.of("pub"),false);
    when(wiki.enabled()).thenReturn(true);when(wiki.fetchInBox(anyDouble(),anyDouble(),anyDouble(),anyDouble(),any(),anyString())).thenReturn(List.of(point));
    when(wiki.enrich(123,"en")).thenReturn(new WikimapiaPoiSource.WikimapiaPoiDetails(point,"The local community knows its history.",List.of("pub"),List.of()));when(wiki.crossCheckOsm(anyList())).thenReturn(Set.of());
    when(osm.search(anyDouble(),anyDouble(),anyInt(),any(),anyString())).thenReturn(List.of(new OsmMcpClient.Poi("node/1","Same pub",37.62,55.75,Map.of("amenity","pub","opening_hours","24/7"))));
    var service=service();try{var result=service.findCandidates(request());assertEquals(1,result.size());assertTrue(result.get(0).inOsm());assertEquals("wikimapia",result.get(0).source());assertEquals("OPEN",result.get(0).visitStatus());assertTrue(result.get(0).description().contains("history"));}
    finally{service.close();}
  }
  @Test void wikimapiaOutageKeepsTheOsmOnlyPath() throws Exception {
    when(wiki.enabled()).thenReturn(true);when(wiki.fetchInBox(anyDouble(),anyDouble(),anyDouble(),anyDouble(),any(),anyString())).thenReturn(List.of());
    when(wiki.crossCheckOsm(anyList())).thenReturn(Set.of());
    when(osm.search(anyDouble(),anyDouble(),anyInt(),any(),anyString())).thenReturn(List.of(new OsmMcpClient.Poi("node/1","Open pub",37.62,55.75,Map.of("amenity","pub","opening_hours","24/7"))));
    var service=service();try{assertEquals("osm",service.findCandidates(request()).get(0).source());}finally{service.close();}
  }
}
