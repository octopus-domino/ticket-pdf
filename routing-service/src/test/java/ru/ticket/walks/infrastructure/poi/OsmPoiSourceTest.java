package ru.ticket.walks.infrastructure.poi;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;
import ru.ticket.walks.infrastructure.poi.OsmPlaceService;
import ru.ticket.walks.infrastructure.mcp.OsmMcpClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OsmPoiSourceTest {
  @Test void unavailableMcpFallsBackToOneUnionOverpassQuery() throws Exception {
    var client=mock(OsmMcpClient.class);var osm=mock(OsmPlaceService.class);when(client.enabled()).thenReturn(true);
    when(client.findNearby(anyDouble(),anyDouble(),anyInt(),anyList(),anyInt())).thenThrow(new IOException("unavailable"));
    when(osm.walkQuery(anyString())).thenReturn(new ObjectMapper().readTree("{\"elements\":[{\"type\":\"node\",\"id\":1,\"lon\":37.62,\"lat\":55.75,\"tags\":{\"name\":\"Courtyard\",\"historic\":\"ruins\"}}]}"));
    var theme=new WalkThemeProperties.Theme(List.of("historic=ruins","abandoned:place=*"),List.of(),List.of(),false);
    var points=new OsmPoiSource(client,osm).search(37.62,55.75,1500,theme,"en");assertEquals(1,points.size());
    verify(osm).walkQuery(argThat(query->query.contains("[\"historic\"=\"ruins\"]")&&query.contains("[\"abandoned:place\"]")));
  }
}
