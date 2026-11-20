package ru.ticket.journey.infrastructure.poi;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PhotonAreaPoiSourceTest {
  @Test void saturatedUpstreamPageIsSplitSpatiallyWithoutTruncatingTotalPlaces() throws Exception {
    var remote = mock(WalkRemote.class);
    var calls = new AtomicInteger();
    when(remote.get(anyString())).thenAnswer(call -> {
      String url = URLDecoder.decode(call.getArgument(0), StandardCharsets.UTF_8);
      assertTrue(url.contains("include=osm.amenity.bar,osm.amenity.place_of_worship"));
      assertFalse(url.contains("lang=ru"));
      int batch = calls.getAndIncrement();
      var result = new ObjectMapper().createObjectNode();
      var features = result.putArray("features");
      for (int index = 0; index < (batch == 0 ? 50 : 20); index++) {
        var feature = features.addObject();
        feature.putObject("geometry").putArray("coordinates").add(30.3054).add(59.9147);
        feature.putObject("properties").put("osm_type", "N").put("osm_id", batch * 100 + index + 1)
            .put("name", "Place " + batch + ":" + index).put("osm_key", "amenity").put("osm_value", "bar");
      }
      return result;
    });
    var theme = new WalkThemeProperties.Theme(List.of("amenity=bar", "amenity=place_of_worship"), List.of(), List.of(), false);
    var result = new PhotonAreaPoiSource(remote, "https://photon.invalid/api/").searchArea(30.3054, 59.9147, 800, theme, "ru");
    assertEquals(130, result.size());
    assertEquals(5, calls.get());
  }
}
