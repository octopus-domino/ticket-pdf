package ru.ticket.journey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.ticket.dto.QrElement;
import ru.ticket.dto.TextElement;

class JourneyDocumentFactoryTest {
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
