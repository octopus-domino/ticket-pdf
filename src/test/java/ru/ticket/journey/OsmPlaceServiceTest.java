package ru.ticket.journey;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OsmPlaceServiceTest {
  private final ObjectMapper mapper = new ObjectMapper();
  @Test void weeklyHoursAreConservativeAndIncludeLunchBreaks() {
    var schedule = OsmOpeningHours.parse("Mo-Fr 09:00-12:00,14:00-18:00; Sa 10:00-16:00; Su off", mapper);
    assertThat(schedule.path("Thu").path("working_hours").size()).isEqualTo(2);
    assertThat(schedule.path("Sun").path("working_hours").isEmpty()).isTrue();
    assertThat(OsmOpeningHours.parse("24/7", mapper).path("Sun").path("working_hours").get(0).path("to").asText()).isEqualTo("24:00");
    assertThat(OsmOpeningHours.parse("sunrise-sunset", mapper).isEmpty()).isTrue();
    assertThat(OsmOpeningHours.parse("Mo-Su 09:00-18:00; PH off", mapper).isEmpty()).isTrue();
    assertThat(OsmOpeningHours.parse("Mo-Su 22:00-04:00", mapper).isEmpty()).isTrue();
  }
  @Test void wayCentersAndTranslatedNamesAreReadWithoutInventingMissingHours() throws Exception {
    var input = mapper.readTree("""
        {"elements":[
          {"type":"way","id":10,"center":{"lon":13.405,"lat":52.52},"tags":{"name":"Museum","name:ru":"Музей","opening_hours":"Mo-Su 10:00-18:00"}},
          {"type":"node","id":20,"lon":13.406,"lat":52.521,"tags":{"name":"Square"}},
          {"type":"node","id":21,"lon":13.406,"lat":52.521,"tags":{"name":"Square"}},
          {"type":"node","id":30,"lon":70,"lat":20,"tags":{"name":"Far away"}}
        ]}
        """);
    var result = OsmPlaceService.readPlaces(input, 13.405, 52.52, "ru", mapper);
    assertThat(result).hasSize(2);
    assertThat(result.get(0).label()).isEqualTo("Музей");
    assertThat(result.get(0).url()).isEqualTo("https://www.openstreetmap.org/way/10");
    assertThat(result.get(1).schedule().isEmpty()).isTrue();
  }
}
