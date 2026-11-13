package ru.ticket.journey.walk;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import ru.ticket.journey.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WikimapiaPoiSourceCrossCheckTest {
  @Test void checksAllPointsWithOneOverpassCallAndCachesTheSet() throws Exception {
    var mapper=new ObjectMapper();var osm=mock(OsmPlaceService.class);var payload=mapper.createObjectNode();var elements=payload.putArray("elements");
    for(int i=0;i<3;i++)elements.addObject().put("lon",37.62+i*.001).put("lat",55.75);
    when(osm.walkQuery(anyString())).thenReturn(payload);
    var source=new WikimapiaPoiSourceImpl(new WikimapiaKeyProvider("test-key"),new WikimapiaRateLimiter(20,5000),mock(WalkRemote.class),osm,"https://example.test/",100);
    var points=IntStream.range(0,5).mapToObj(i->new WikimapiaPoiSource.WikimapiaPoi(i+1,"Point "+i,37.62+i*.001,55.75,List.of(),false)).toList();
    assertEquals(Set.of(1L,2L,3L),source.crossCheckOsm(points));assertEquals(Set.of(1L,2L,3L),source.crossCheckOsm(points));
    verify(osm,times(1)).walkQuery(argThat(query->query.split("around:15",-1).length==6));
  }
}
