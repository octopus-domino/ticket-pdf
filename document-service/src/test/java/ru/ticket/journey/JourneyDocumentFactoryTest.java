package ru.ticket.journey;

import ru.ticket.journey.infrastructure.pdf.JourneyDocumentFactory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.ticket.dto.QrElement;
import ru.ticket.dto.TextElement;
import ru.ticket.journey.domain.model.Trip;

class JourneyDocumentFactoryTest {
  @Test
  void tripProjectionShowsLocalTransferTimesAndPrivateItinerarySections() throws Exception {
    var date = LocalDate.of(2027, 1, 1);
    var from = new Trip.City("a", "Petersburg", null, null, null, "Europe/Moscow", null);
    var to = new Trip.City("b", "Kazan", null, null, null, "Europe/Moscow", null);
    var transfer = new Trip.Leg("rail", "a", "b", Trip.Transport.TRAIN,
        ZonedDateTime.parse("2027-01-01T10:00:00+03:00"),
        ZonedDateTime.parse("2027-01-01T15:00:00+04:00"), null, null, null, "002");
    var hotel = new Trip.Accommodation("hotel", "b", "Mirage", "Main Street", null, null,
        date, date.plusDays(1), null, null, Trip.HotelProvider.MANUAL, null);
    var document = new Trip.Document("doc", "Reservation", "https://example.org/reservation");
    var budget = new Trip.Budget(new Trip.Money(BigDecimal.valueOf(1000), "RUB"));
    var trip = new Trip(2, "itinerary", "Winter trip", null, date, date.plusDays(1), "RUB",
        List.of(), List.of(from, to), List.of(new Trip.Day(date, "a", List.of(), null, "Morning walk")),
        List.of(transfer), List.of(hotel), List.of(document), budget,
        new Trip.Meta(Trip.Mode.TRIP, Trip.Source.MANUAL, null, null, null, Map.of()));
    var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    var reopened = mapper.readValue(mapper.writeValueAsString(trip), Trip.class);
    var pages = new JourneyDocumentFactory("codoc.site", mock(JourneySiteService.class)).trip(reopened).slides;
    assertThat(pages.stream().flatMap(page -> page.elements.stream()).map(TextElement.class::cast)
        .map(element -> element.text).toList()).anySatisfy(body -> assertThat(body)
        .contains("Morning walk", "Petersburg · 2027-01-01 10:00 +03:00", "Kazan · 2027-01-01 15:00 +04:00",
            "Mirage", "1000 RUB", "Reservation"));
  }

  @Test
  void customFieldsAndThemeArePrintedAndLongContentIsPaginated() {
    var sites = mock(JourneySiteService.class);
    when(sites.get("my-cafe"))
        .thenReturn(
            new JourneySiteService.Definition(
                "my-cafe",
                JourneyService.Kind.MENU,
                "Cafe",
                "",
                "ink",
                List.of(
                    new JourneySiteService.Field("special", "Special menu", "textarea", true))));
    String content = "Pasta and vegetables, fresh bread and dessert. ".repeat(100);
    var view =
        new JourneyService.View(
            "Abc123Def456",
            JourneyService.Kind.MENU,
            "Cafe",
            Map.of("site", "my-cafe", "special", content),
            Instant.now());
    var pages = new JourneyDocumentFactory("codoc.site", sites).document(view).slides;
    assertThat(pages.size()).isGreaterThan(1);
    assertThat(pages)
        .allSatisfy(
            page -> {
              assertThat(page.background).isEqualTo("#202e3c");
              assertThat(page.elements)
                  .filteredOn(element -> element instanceof QrElement)
                  .allSatisfy(
                      element ->
                          assertThat(((QrElement) element).text)
                              .isEqualTo("https://my-cafe.codoc.site/d/Abc123Def456"));
              assertThat(page.elements)
                  .filteredOn(element -> element instanceof TextElement)
                  .allSatisfy(
                      element -> {
                        assertThat(element.height).isPositive();
                        assertThat(element.y + element.height).isLessThanOrEqualTo(page.height);
                      });
            });
    assertThat(
            pages.get(0).elements.stream()
                .filter(element -> element instanceof TextElement)
                .map(element -> ((TextElement) element).text)
                .toList())
        .anyMatch(text -> text.contains("Special menu:"));
  }
}
