package ru.ticket.journey.infrastructure.poi;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.ticket.mcp.OsmMcpClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TiledAreaOsmSearchTest {
  private static final double LON = 30.3125, LAT = 59.93125;
  private static final WalkThemeProperties.Theme BARS =
      new WalkThemeProperties.Theme(List.of("amenity=bar"), List.of(), List.of(), false);
  private static final OsmMcpClient.Poi BAR =
      new OsmMcpClient.Poi("node/42", "Bar", LON, LAT, Map.of("amenity", "bar"));

  @Test void expansionReusesTheInnerCellAndDeduplicatesPoints() throws Exception {
    var osm = mock(OsmPoiSource.class);
    var queried = new HashSet<Double>();
    when(osm.searchBox(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList(), eq("ru")))
        .thenAnswer(invocation -> {
          queried.add(invocation.<Double>getArgument(0) * 1000 + invocation.<Double>getArgument(1));
          return List.of(BAR);
        });
    var search = new TiledAreaOsmSearch(osm);
    assertTrue(search.search(LON, LAT, 400, BARS, "ru").complete());
    assertEquals(1, queried.size());
    var expanded = search.search(LON, LAT, 900, BARS, "ru");
    assertTrue(expanded.complete());
    assertEquals(List.of(BAR), expanded.points());
    assertTrue(queried.size() > 1);
    verify(osm, times(queried.size())).searchBox(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList(), eq("ru"));
  }

  @Test void newlySelectedCategoriesShareOneRequestAndReuseCachedBars() throws Exception {
    var osm = mock(OsmPoiSource.class);
    var newSelectors = List.of("tourism=gallery", "tourism=museum");
    when(osm.searchBox(anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq(BARS.tags()), eq("ru")))
        .thenReturn(List.of(BAR));
    when(osm.searchBox(anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq(newSelectors), eq("ru")))
        .thenReturn(List.of(new OsmMcpClient.Poi("node/43", "Museum", LON, LAT, Map.of("tourism", "museum"))));
    var search = new TiledAreaOsmSearch(osm);
    search.search(LON, LAT, 400, BARS, "ru");
    var combined = new WalkThemeProperties.Theme(List.of("amenity=bar", "tourism=museum", "tourism=gallery"),
        List.of(), List.of(), false);
    var result = search.search(LON, LAT, 400, combined, "ru");
    assertTrue(result.complete());
    assertEquals(List.of("node/42", "node/43"), result.points().stream().map(OsmMcpClient.Poi::id).toList());
    assertEquals(result, search.search(LON, LAT, 400, combined, "ru"));
    verify(osm, times(2)).searchBox(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList(), eq("ru"));
  }

  @Test void aFailedOuterCellDoesNotDiscardCachedPointsOrMarkCoverageComplete() throws Exception {
    var osm = mock(OsmPoiSource.class);
    when(osm.searchBox(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList(), eq("ru")))
        .thenReturn(List.of(BAR)).thenThrow(new IOException("overpass"));
    var search = new TiledAreaOsmSearch(osm);
    assertTrue(search.search(LON, LAT, 400, BARS, "ru").complete());
    var expanded = search.search(LON, LAT, 900, BARS, "ru");
    assertTrue(expanded.available());
    assertFalse(expanded.complete());
    assertEquals(List.of(BAR), expanded.points());
    assertTrue(search.search(LON, LAT, 400, BARS, "ru").complete());
  }

  @Test void anOversizedCellIsSplitAndExcludedFromTheCompleteCache() throws Exception {
    var osm = mock(OsmPoiSource.class);
    when(osm.searchBox(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList(), eq("ru")))
        .thenThrow(new OsmPoiSource.DenseCell()).thenReturn(List.of(BAR));
    var search = new TiledAreaOsmSearch(osm);
    var result = search.search(LON, LAT, 400, BARS, "ru");
    assertTrue(result.complete());
    assertEquals(List.of(BAR), result.points());
    assertTrue(search.search(LON, LAT, 400, BARS, "ru").complete());
    verify(osm, times(5)).searchBox(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList(), eq("ru"));
  }
}
