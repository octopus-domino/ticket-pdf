package ru.ticket.journey.infrastructure.poi;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.ticket.journey.OsmPlaceService;
import ru.ticket.mcp.OsmMcpClient;

class OsmAreaQueryTest {
  @Test void areaQueriesRequestNodeCoordinatesAndNeverTruncateAtSevenOrEighty() throws Exception {
    var mapper = new ObjectMapper();
    var payload = mapper.createObjectNode();
    var elements = payload.putArray("elements");
    for (int index = 0; index < 100; index++) {
      var node = elements.addObject().put("id", index + 1).put("type", "node").put("lat", 59.94).put("lon", 30.33);
      node.putObject("tags").put("name", "Bar " + index).put("amenity", "bar");
    }
    var osm = mock(OsmPlaceService.class);
    when(osm.walkQuery(anyString())).thenReturn(payload);
    var mcp = mock(OsmMcpClient.class);
    var source = new OsmPoiSource(mcp, osm);
    try {
      var theme = new WalkThemeProperties.Theme(List.of("amenity=bar"), List.of(), List.of(), false);
      assertEquals(100, source.searchArea(30.33, 59.94, 500, theme, "ru").size());
      var query = ArgumentCaptor.forClass(String.class);
      verify(osm).walkQuery(query.capture());
      assertTrue(query.getValue().endsWith("out body center;"));
      verifyNoInteractions(mcp);
    } finally { source.close(); }
  }
}
