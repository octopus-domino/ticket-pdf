package ru.ticket.journey.walk;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;

class PhotonPoiSourceTest {
  private final ObjectMapper mapper=new ObjectMapper();
  private final WalkThemeProperties.Theme theme=new WalkThemeProperties.Theme(List.of("amenity=bar"),List.of(),List.of(),true);
  @Test void preservesCategoryRequestsAndParsesActualPhotonFeaturesWithoutRussianLangOrEmptyQuery() throws Exception {
    var remote=mock(WalkRemote.class);
    when(remote.get(anyString())).thenReturn(mapper.readTree("""
        {"type":"FeatureCollection","features":[
          {"properties":{"osm_type":"N","osm_id":77,"name":"Station bar","osm_key":"amenity","osm_value":"bar"},"geometry":{"type":"Point","coordinates":[30.359,59.93]}},
          {"properties":{"osm_type":"W","osm_id":78,"name":"Courtyard pub","amenity":"pub"},"geometry":{"type":"Point","coordinates":[30.36,59.931]}}
        ]}
        """));
    var source=new PhotonPoiSource(mapper,remote,"https://photon.example/api/");
    var points=source.search(30.359,59.93,1500,theme,"ru");
    assertEquals(2,points.size());assertEquals("node/77",points.get(0).id());assertEquals("way/78",points.get(1).id());
    assertEquals("bar",points.get(0).tags().get("amenity"));assertEquals(30.359,points.get(0).lon());
    var request=org.mockito.ArgumentCaptor.forClass(String.class);verify(remote).get(request.capture());
    assertTrue(request.getValue().contains("include=osm.amenity.bar"));assertTrue(request.getValue().contains("bbox="));
    assertFalse(request.getValue().contains("lang=ru"));assertFalse(request.getValue().contains("q="));assertFalse(request.getValue().contains("osm_tag="));
  }
  @Test void anEmptyResultIsRetriedInsteadOfBeingCached() throws Exception {
    var remote=mock(WalkRemote.class);
    when(remote.get(anyString())).thenReturn(mapper.readTree("{\"features\":[]}"),mapper.readTree("""
        {"features":[{"properties":{"osm_type":"N","osm_id":77,"name":"Recovered bar","osm_key":"amenity","osm_value":"bar"},"geometry":{"coordinates":[30.359,59.93]}}]}
        """));
    var source=new PhotonPoiSource(mapper,remote,"https://photon.example/api/");
    assertTrue(source.search(30.359,59.93,1500,theme,"ru").isEmpty());
    assertEquals(1,source.search(30.359,59.93,1500,theme,"ru").size());verify(remote,times(2)).get(anyString());
  }
}
