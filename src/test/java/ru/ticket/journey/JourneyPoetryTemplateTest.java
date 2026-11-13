package ru.ticket.journey;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import ru.ticket.dto.QrElement;
import ru.ticket.dto.TextElement;
import ru.ticket.journey.poetry.PoetryCardsService;
import static org.assertj.core.api.Assertions.assertThat;

class JourneyPoetryTemplateTest {
  @Test void sevenCardsUseNativeRepeatAndRemainIndependentlyEditable() {
    var cards = new ArrayList<PoetryCardsService.Card>();
    for (int day = 1; day <= 7; day++) cards.add(new PoetryCardsService.Card("2026-11-0" + day,
        "Test card " + day, "Synthetic layout fixture " + day, "Test author", "Q1", "https://example.test/author",
        "Q2", "https://example.test/source/" + day, "https://example.test/license"));
    var view = new JourneyService.View("Abc123Def456", JourneyService.Kind.POETRY, "Collection", Map.of(), Instant.now());
    var source = JourneyPoetryTemplate.source(view, new PoetryCardsService.Bundle("en", "heartfelt", "middle", cards), "https://codoc.site/d/Abc123Def456");
    assertThat(source.template().slides()).hasSize(1);
    assertThat(source.template().slides().get(0).repeat.path()).isEqualTo("cards");
    var pages = source.materialize().slides;
    assertThat(pages).hasSize(7);
    for (int index = 0; index < 7; index++) {
      final int day = index + 1;
      assertThat(pages.get(index).elements).filteredOn(element -> element instanceof TextElement)
          .anySatisfy(element -> assertThat(((TextElement) element).text).isEqualTo("Synthetic layout fixture " + day));
      assertThat(pages.get(index).elements).filteredOn(element -> element instanceof QrElement)
          .allSatisfy(element -> assertThat(((QrElement) element).text).isEqualTo("https://codoc.site/d/Abc123Def456"));
    }
  }
}
