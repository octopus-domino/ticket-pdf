package ru.ticket.journey;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.ticket.common.ApiException;
import ru.ticket.dto.TextElement;

class ProposalContentTest {
  @Test
  void calculatesRoundedLineTotalsAndRejectsInvalidMoney() {
    var items =
        ProposalContent.read("[{\"name\":\"Service\",\"quantity\":\"1.5\",\"price\":\"10.01\"}]");
    assertThat(ProposalContent.total(items)).isEqualTo("15.02");
    for (String amount : new String[] {"-1", "NaN", "1e9", "10000000000", "=SUM(A1)"}) {
      assertThatThrownBy(
              () ->
                  ProposalContent.read(
                      "[{\"name\":\"A\",\"quantity\":\"1\",\"price\":\"" + amount + "\"}]"))
          .isInstanceOf(ApiException.class);
    }
  }

  @Test
  void paginatesWithoutLosingPositionsAndPrintsTotalOnce() {
    String json =
        "["
            + java.util.stream.IntStream.range(0, 100)
                .mapToObj(
                    i ->
                        "{\"name\":\"Product "
                            + i
                            + " "
                            + "Long name ".repeat(15)
                            + "\",\"quantity\":\"1\",\"price\":\"10.01\"}")
                .collect(java.util.stream.Collectors.joining(","))
            + "]";
    var view =
        new JourneyService.View(
            "Abc123Def456",
            JourneyService.Kind.PROPOSAL,
            "W".repeat(120),
            Map.of("positions", json, "issuer", "W".repeat(100), "recipient", "Client"),
            Instant.now());
    var pages =
        new JourneyDocumentFactory("codoc.site", mock(JourneySiteService.class))
            .document(view)
            .slides;
    assertThat(pages.size()).isGreaterThan(1);
    var texts =
        pages.stream()
            .flatMap(p -> p.elements.stream())
            .filter(e -> e instanceof TextElement)
            .map(e -> (TextElement) e)
            .toList();
    assertThat(texts.stream().filter(t -> t.text.equals("ИТОГО")).toList())
        .hasSize(1)
        .first()
        .extracting(t -> t.text)
        .isEqualTo("ИТОГО");
    assertThat(texts.stream().map(t -> t.text)).contains("1\u00a0001,00 ₽");
    for (int i = 0; i < 100; i++) {
      String prefix = "Product " + i + " ";
      assertThat(texts.stream().filter(t -> t.text.startsWith(prefix))).hasSize(1);
    }
    for (var page : pages) {
      assertThat(page.elements)
          .allSatisfy(
              e -> {
                assertThat(e.y + e.height).isLessThanOrEqualTo(page.height);
                assertThat(e.x + e.width).isLessThanOrEqualTo(page.width);
              });
      assertThat(
              page.elements.stream()
                  .filter(e -> e instanceof TextElement)
                  .map(e -> ((TextElement) e).text))
          .contains("Наименование", "Кол-во", "Цена, ₽", "Сумма, ₽");
    }
  }
}
