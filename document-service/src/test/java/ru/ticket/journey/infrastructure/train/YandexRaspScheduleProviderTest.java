package ru.ticket.journey.infrastructure.train;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import ru.ticket.journey.domain.service.TrainScheduleProvider.Query;
import ru.ticket.journey.domain.service.TrainScheduleProvider.Place;

class YandexRaspScheduleProviderTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void preservesPublishedOffsetsAndSkipsMalformedSegments() throws Exception {
    var response = mapper.readTree("""
        {"segments":[{"departure":"2026-12-31T12:00:00+03:00","arrival":"2026-12-31T16:00:00+05:00",
            "from":{"title":"A"},"to":{"title":"B"},"thread":{"number":"014","title":"A → B"}},
            {"departure":null,"arrival":null}]}
        """);
    assertThat(YandexRaspScheduleProvider.readTrains(response)).singleElement().satisfies(train -> {
      assertThat(train.number()).isEqualTo("014");
      assertThat(train.arrival().getOffset()).isEqualTo(ZoneOffset.ofHours(5));
      assertThat(Duration.between(train.departure(), train.arrival())).isEqualTo(Duration.ofHours(2));
    });
  }

  @Test
  void resolvesRailwaySettlementsAndStationsWithoutIncludingOtherTransport() throws Exception {
    var response = mapper.readTree("""
        {"countries":[{"title":"Russia","regions":[{"title":"Region","settlements":[
          {"title":"City","codes":{"yandex_code":"c1"},"stations":[
            {"title":"Garage","transport_type":"bus","codes":{"yandex_code":"s3"}},
            {"title":"Station","transport_type":"train","codes":{"yandex_code":"s2"}}]},
          {"title":"Other","codes":{"yandex_code":"c4"},"stations":[
            {"title":"Airport","transport_type":"plane","codes":{"yandex_code":"s5"}}]}
        ]}]}]}
        """);
    assertThat(YandexRaspScheduleProvider.readPlaces(response))
        .containsExactly(new Place("c1", "City", "Region"), new Place("s2", "Station", "City"));
  }

  @Test
  void missingKeyProducesAnExplicitlyUnavailableResult() {
    var provider = new YandexRaspScheduleProvider(mapper, "");
    var result = provider.search(new Query("c213", "c2", LocalDate.of(2026, 12, 31)));
    assertThat(result.available()).isFalse();
    assertThat(result.trains()).isEmpty();
  }
}
