package ru.ticket.journey;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.ticket.journey.walk.WalkAreaSearchService;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AreaPlacesControllerTest {
  @Test void areaCacheIsAccessibleWhileLegacySearchIsBusy() throws Exception {
    var controller = new PlaceRecommendationsController(new ObjectMapper(), "", mock(OsmPlaceService.class));
    var service = mock(WalkAreaSearchService.class);
    controller.areas(service);
    when(service.search(any())).thenReturn(List.of());
    var field = PlaceRecommendationsController.class.getDeclaredField("searchPermit");
    field.setAccessible(true);
    var permit = (java.util.concurrent.Semaphore) field.get(controller);
    permit.acquire();
    try {
      org.junit.jupiter.api.Assertions.assertTrue(controller.themedAreaSearch(30.3053, 59.93155,
          List.of("bars"), "ru", .8, null).available());
    } finally { permit.release(); }
  }

  @Test void themeListUsesOneAreaSearchWithoutRequiringLegacyThemeOrMaxStops() throws Exception {
    var controller = new PlaceRecommendationsController(new ObjectMapper(), "", mock(OsmPlaceService.class));
    var service = mock(WalkAreaSearchService.class);
    controller.areas(service);
    when(service.search(any())).thenReturn(List.of());
    MockMvcBuilders.standaloneSetup(controller).build().perform(get("/api/journeys/routes/places/themed")
        .param("longitude", "30.3236").param("latitude", "59.9259").param("language", "ru")
        .param("themes", "bars,religious").param("radiusKm", "1"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));
    verify(service).search(argThat(request -> request.themes().equals(List.of("bars", "religious")) && request.radiusKm() == 1));
  }
}
