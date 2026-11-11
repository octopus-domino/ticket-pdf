package ru.ticket.journey;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import ru.ticket.dto.TextElement;

class EventDocumentLayoutTest {
  @Test
  void localPreviewAndPdfQrUseTheConfiguredLocalFrontend() {
    var local =
        new JourneyDocumentFactory(
            "codoc.site", mock(JourneySiteService.class), "http://localhost:5173");
    var view =
        new JourneyService.View(
            "Abc123Def456", JourneyService.Kind.INVITATION, "Event", Map.of(), Instant.EPOCH);
    assertThat(local.publicUrl(view)).isEqualTo("http://localhost:5173/d/Abc123Def456");
  }

  private final JourneyDocumentFactory factory =
      new JourneyDocumentFactory("codoc.site", mock(JourneySiteService.class));

  @Test
  void usesHumanDateOnceAndDoesNotRepeatMatchingExpiry() {
    var view =
        new JourneyService.View(
            "IE4p5XZsOxdx",
            JourneyService.Kind.TICKET,
            "Билеты с проверкой входа",
            Map.of(
                "theme",
                "ink",
                "recipient",
                "Вася",
                "description",
                "Ждём вас на встрече.",
                "date",
                "2026-11-13",
                "time",
                "09:34",
                "valid_until",
                "2026-11-13",
                "location",
                "В Поле"),
            Instant.EPOCH);
    var pages = factory.document(view).slides;
    assertThat(pages).hasSize(1);
    var text =
        pages.get(0).elements.stream()
            .filter(e -> e instanceof TextElement)
            .map(e -> ((TextElement) e).text)
            .toList();
    assertThat(text).contains("13 ноября 2026  ·  09:34", "В Поле", "Вася");
    assertThat(String.join("\n", text)).doesNotContain("2026-11-13", "Действует по");
  }

  @Test
  void preservesLongContentAcrossPagesAndKeepsAllTextInsidePaper() {
    String description =
        java.util.stream.IntStream.range(0, 180)
            .mapToObj(i -> "Строка " + i + ": важная информация для гостей.")
            .collect(java.util.stream.Collectors.joining("\n"));
    var view =
        new JourneyService.View(
            "Abc123Def456",
            JourneyService.Kind.INVITATION,
            "Приглашение на праздник",
            Map.of("description", description, "recipient", "В".repeat(200), "theme", "rose"),
            Instant.EPOCH);
    var pages = factory.document(view).slides;
    assertThat(pages.size()).isGreaterThan(1);
    var text =
        pages.stream()
            .flatMap(p -> p.elements.stream())
            .filter(e -> e instanceof TextElement)
            .map(e -> ((TextElement) e).text)
            .toList();
    for (int i = 0; i < 180; i++) {
      String expected = "Строка " + i + ":";
      assertThat(text.stream().filter(t -> t.startsWith(expected))).hasSize(1);
    }
    for (var page : pages)
      assertThat(page.elements)
          .filteredOn(e -> e instanceof TextElement)
          .allSatisfy(
              e -> {
                assertThat(e.x + e.width).isLessThanOrEqualTo(page.width);
                assertThat(e.y + e.height).isLessThanOrEqualTo(page.height);
              });
  }
}
