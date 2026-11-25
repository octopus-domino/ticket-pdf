package ru.ticket.journey.infrastructure.pdf;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoutePrintLabelsTest {
  @Test void onlyIntermediateStopsHaveNumbersAndEndpointsHaveNames() {
    assertEquals("Начало маршрута",RoutePrintLabels.pointCaption(0,2,"ru",Map.of()));
    assertEquals("Конец маршрута",RoutePrintLabels.pointCaption(1,2,"ru",Map.of()));
    assertEquals("Остановка 1",RoutePrintLabels.pointCaption(1,4,"ru",Map.of()));
    assertEquals("Остановка 2",RoutePrintLabels.pointCaption(2,4,"ru",Map.of()));
    assertEquals("Конец маршрута",RoutePrintLabels.pointCaption(3,4,"ru",Map.of()));
    assertEquals("A",RoutePrintLabels.pointMarker(0,4));
    assertEquals("1",RoutePrintLabels.pointMarker(1,4));
    assertEquals("B",RoutePrintLabels.pointMarker(3,4));
  }
  @Test void allNineLanguagesHaveEndpointLabelsWithoutStopNumbers() {
    for(String language:java.util.List.of("en","ru","zh","pl","it","es","nl","fr","de")) {
      assertFalse(RoutePrintLabels.pointCaption(0,2,language,Map.of()).matches(".*[0-9].*"));
      assertNotEquals(RoutePrintLabels.pointCaption(0,2,language,Map.of()),RoutePrintLabels.pointCaption(1,2,language,Map.of()));
    }
  }
}
