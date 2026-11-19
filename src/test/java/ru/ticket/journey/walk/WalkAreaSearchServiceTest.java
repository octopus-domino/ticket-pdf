package ru.ticket.journey.walk;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import ru.ticket.journey.*;
import ru.ticket.mcp.OsmMcpClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WalkAreaSearchServiceTest {
  @Test void primarySuccessAfterEightSecondsSurvivesFailedFallback() throws Exception {
    var osm = mock(OsmPoiSource.class);
    when(osm.searchArea(anyDouble(), anyDouble(), anyInt(), any(), anyString())).thenAnswer(call -> {
      Thread.sleep(8300);
      return List.of(new OsmMcpClient.Poi("node/91", "Bar", 30.3054, 59.93155, Map.of("amenity", "bar")));
    });
    var fallback = mock(PhotonAreaPoiSource.class);
    when(fallback.search(anyDouble(), anyDouble(), anyInt(), any(), anyString())).thenThrow(new java.io.IOException("503"));
    var themes = new WalkThemeProperties();
    themes.setWalkThemes(Map.of("bars", new WalkThemeProperties.Theme(List.of("amenity=bar"), List.of(), List.of(), false)));
    var service = new WalkAreaSearchService(themes, osm, mock(WikimapiaPoiSource.class), mock(MysticThemeSource.class),
        new OpeningHoursEvaluator(new ObjectMapper()), new ObjectMapper(), fallback);
    try {
      assertEquals(1, service.search(new WalkAreaSearchService.Request(30.3054, 59.93155, List.of("bars"), "ru", 3.1, null)).size());
      assertEquals(1, service.search(new WalkAreaSearchService.Request(30.3054, 59.93155, List.of("bars"), "ru", .8, null)).size());
      verify(osm, times(1)).searchArea(anyDouble(), anyDouble(), anyInt(), any(), anyString());
    } finally { service.close(); }
  }

  @Test void overloadedOverpassUsesAvailableAreaFallbackInsteadOfReturning503() throws Exception {
    var osm = mock(OsmPoiSource.class);
    when(osm.searchArea(anyDouble(), anyDouble(), anyInt(), any(), anyString())).thenThrow(new java.io.IOException("HTTP 504"));
    var fallback = mock(PhotonAreaPoiSource.class);
    when(fallback.search(anyDouble(), anyDouble(), anyInt(), any(), anyString())).thenReturn(List.of(
        new OsmMcpClient.Poi("node/91", "Nearby bar", 30.3054, 59.9147, Map.of("amenity", "bar"))));
    var themes = new WalkThemeProperties();
    themes.setWalkThemes(Map.of("bars", new WalkThemeProperties.Theme(List.of("amenity=bar"), List.of(), List.of(), false)));
    var service = new WalkAreaSearchService(themes, osm, mock(WikimapiaPoiSource.class), mock(MysticThemeSource.class),
        new OpeningHoursEvaluator(new ObjectMapper()), new ObjectMapper(), fallback);
    try {
      var result = service.search(new WalkAreaSearchService.Request(30.3054, 59.9147, List.of("bars"), "ru", .8, null));
      assertEquals(1, result.size());
      assertEquals("bars", result.get(0).meta().get("themes"));
    } finally { service.close(); }
  }
  @Test void combinesThemesOnceReturnsMoreThanSevenAndPreservesActualCategories() throws Exception {
    var osm = mock(OsmPoiSource.class);
    var wiki = mock(WikimapiaPoiSource.class);
    var themes = new WalkThemeProperties();
    themes.setWalkThemes(Map.of("bars", new WalkThemeProperties.Theme(List.of("amenity=bar"), List.of(), List.of(), false),
        "religious", new WalkThemeProperties.Theme(List.of("amenity=place_of_worship"), List.of(), List.of(), false)));
    var points = IntStream.range(0, 18).mapToObj(index -> new OsmMcpClient.Poi("node/" + (index + 1), "Place " + index,
        30.3236 + index * .0001, 59.9259, Map.of("amenity", index < 10 ? "bar" : "place_of_worship"))).toList();
    when(osm.searchArea(anyDouble(), anyDouble(), anyInt(), any(), anyString())).thenAnswer(call -> {
      WalkThemeProperties.Theme combined = call.getArgument(3);
      assertTrue(combined.tags().containsAll(List.of("amenity=bar", "amenity=place_of_worship")));
      return points;
    });
    var service = new WalkAreaSearchService(themes, osm, wiki, mock(MysticThemeSource.class),
        new OpeningHoursEvaluator(new ObjectMapper()), new ObjectMapper());
    try {
      var result = service.search(new WalkAreaSearchService.Request(30.3236, 59.9259, List.of("bars", "religious"), "ru", 1, null));
      assertEquals(18, result.size());
      assertEquals(10, result.stream().filter(place -> place.meta().get("themes").equals("bars")).count());
      assertEquals(8, result.stream().filter(place -> place.meta().get("themes").equals("religious")).count());
      assertEquals(result, service.search(new WalkAreaSearchService.Request(30.3236, 59.9259, List.of("religious", "bars"), "ru", 1, null)));
      verify(osm, times(1)).searchArea(anyDouble(), anyDouble(), eq(1000), any(), eq("ru"));
      verify(osm, never()).search(anyDouble(), anyDouble(), anyInt(), any(), anyString());
    } finally { service.close(); }
  }

  @Test void unknownThemeFailsBeforeMakingProviderRequests() {
    var osm = mock(OsmPoiSource.class);
    var wiki = mock(WikimapiaPoiSource.class);
    var service = new WalkAreaSearchService(new WalkThemeProperties(), osm, wiki, mock(MysticThemeSource.class),
        new OpeningHoursEvaluator(new ObjectMapper()), new ObjectMapper());
    try {
      assertThrows(ru.ticket.common.ApiException.class, () -> service.search(
          new WalkAreaSearchService.Request(30.32, 59.92, List.of("unconfigured"), "ru", 1, null)));
      verifyNoInteractions(osm, wiki);
    } finally { service.close(); }
  }
}
