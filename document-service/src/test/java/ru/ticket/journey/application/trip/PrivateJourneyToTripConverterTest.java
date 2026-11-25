package ru.ticket.journey.application.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.ticket.common.ApiException;
import ru.ticket.journey.application.journey.PrivateJourneyReader;
import ru.ticket.journey.application.journey.PrivateJourneyReader.Edge;
import ru.ticket.journey.application.journey.PrivateJourneyReader.Point;
import ru.ticket.journey.application.journey.PrivateJourneyReader.Route;
import ru.ticket.journey.application.journey.PrivateJourneyReader.Transfer;
import ru.ticket.journey.domain.model.Trip;

class PrivateJourneyToTripConverterTest {
  private final PrivateJourneyToTripConverter converter =
      new PrivateJourneyToTripConverter(new PrivateJourneyReader());
  private final LocalDate date = LocalDate.of(2026, 12, 31);

  @Test
  void copiesTheWalkIntoASeparateTwoDayDraft() {
    var geometry = new ArrayList<>(List.of(List.of(59.9, 30.3), List.of(59.91, 30.31)));
    var input = transfer(new Edge(0, 1, geometry, 1500, 900));
    var trip = converter.convert(input, date, " Петербург ");
    assertThat(trip.id()).isNotEqualTo(converter.convert(input, date, null).id());
    assertThat(trip.endDate()).isEqualTo(LocalDate.of(2027, 1, 1));
    assertThat(trip.meta().source()).isEqualTo(Trip.Source.PRIVATE_JOURNEY);
    assertThat(trip.cities().get(0).name()).isEqualTo("Петербург");
    var day = trip.days().get(0);
    assertThat(day.segments()).extracting(Trip.Segment::title).containsExactly("Начало", "2");
    assertThat(day.segments().get(0).note()).isEqualTo("Заметка\nhttps://example.org/house");
    geometry.clear();
    assertThat(day.walkingSections()).containsExactly(new Trip.WalkingSection("stop-1", "stop-2",
        List.of(new Trip.Coordinate(59.9, 30.3), new Trip.Coordinate(59.91, 30.31)), 1500, 900));
  }

  @Test
  void rejectsAnUnorderedOrInvalidTransferAndRequiresAnExplicitDate() {
    assertThatThrownBy(() -> converter.convert(transfer(new Edge(1, 0, List.of(), 1, 1)), date, null))
        .isInstanceOf(ApiException.class).hasMessage("journey.trip.invalid-private-walk");
    var invalid = transfer(new Edge(0, 1, List.of(List.of(59.9, 30.3), List.of(Double.NaN, 30.31)), 1, 1));
    assertThatThrownBy(() -> converter.convert(invalid, date, null))
        .isInstanceOf(ApiException.class).hasMessage("journey.trip.invalid-private-walk");
    assertThatThrownBy(() -> converter.convert(invalid, null, null))
        .isInstanceOf(ApiException.class).hasMessage("journey.trip.choose-start-date");
  }

  @Test
  void acceptsTheExistingBrowserEnvelopeButRejectsMissingCoordinates() throws Exception {
    String json = """
        {"version":1,"id":"local-id","title":"Walk","createdAt":"2026-11-01T09:00:00Z",
         "points":[{"lat":59.9,"lng":30.3,"poiId":"house"},{"lat":59.91,"lng":30.31}],
         "route":{"geometry":[[59.9,30.3],[59.91,30.31]],"totalDurationSeconds":900,
          "legs":[{"fromIndex":0,"toIndex":1,"geometry":[[59.9,30.3],[59.91,30.31]],
                   "distanceMeters":1500,"durationSeconds":900}]}}
        """;
    var mapper = new ObjectMapper();
    var trip = converter.convert(mapper.readValue(json, Transfer.class), date, null);
    assertThat(trip.days().get(0).walkingSections()).hasSize(1);
    var missing = mapper.readValue(json.replace("\"lat\":59.9,\"lng\":30.3", "\"lat\":59.9"), Transfer.class);
    assertThatThrownBy(() -> converter.convert(missing, date, null))
        .isInstanceOf(ApiException.class).hasMessage("journey.trip.invalid-private-walk");
  }

  private static Transfer transfer(Edge edge) {
    return new Transfer(1, "Прогулка", List.of(
        new Point(59.9, 30.3, "Начало", "Заметка", "https://example.org/house"),
        new Point(59.91, 30.31, null, null, null)), new Route(List.of(edge)));
  }
}
