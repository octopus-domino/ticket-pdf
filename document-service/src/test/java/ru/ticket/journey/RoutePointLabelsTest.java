package ru.ticket.journey;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoutePointLabelsTest {
  @Test void onlyIntermediateStopsHaveNumbersAndEndpointsHaveNames() {
    assertEquals("Начало маршрута",RoutePointLabels.caption(0,2,"ru",Map.of()));
    assertEquals("Конец маршрута",RoutePointLabels.caption(1,2,"ru",Map.of()));
    assertEquals("Остановка 1",RoutePointLabels.caption(1,4,"ru",Map.of()));
    assertEquals("Остановка 2",RoutePointLabels.caption(2,4,"ru",Map.of()));
    assertEquals("Конец маршрута",RoutePointLabels.caption(3,4,"ru",Map.of()));
    assertEquals("A",RoutePointLabels.marker(0,4));
    assertEquals("1",RoutePointLabels.marker(1,4));
    assertEquals("B",RoutePointLabels.marker(3,4));
  }
  @Test void allNineLanguagesHaveEndpointLabelsWithoutStopNumbers() {
    for(String language:java.util.List.of("en","ru","zh","pl","it","es","nl","fr","de")) {
      assertFalse(RoutePointLabels.caption(0,2,language,Map.of()).matches(".*[0-9].*"));
      assertNotEquals(RoutePointLabels.caption(0,2,language,Map.of()),RoutePointLabels.caption(1,2,language,Map.of()));
    }
  }
}
