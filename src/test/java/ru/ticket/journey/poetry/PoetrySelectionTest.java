package ru.ticket.journey.poetry;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PoetrySelectionTest {
  @Test void shortAdultRomanceIsNotAChildrensPoem() {
    assertThat(PoetrySelection.suitable(poem("Love lyric", "love heart gentle warm home", "Poems"),"en","heartfelt","child")).isFalse();
    assertThat(PoetrySelection.suitable(poem("Nursery fixture", "A bird sings in a garden", "Children's poems"),"en","heartfelt","child")).isTrue();
    assertThat(PoetrySelection.suitable(poem("Nursery fixture", "blood and murder", "Children's poems"),"en","heartfelt","child")).isFalse();
  }
  @Test void forecastChangesRankingWithoutAddingWeatherTextToCards() {
    var poems = new ArrayList<WikisourcePoems.Poem>();
    for (int i = 0; i < 7; i++) {
      poems.add(poem("Warm " + i, "love heart friend gentle warm home", ""));
      poems.add(poem("Joy " + i, "joy laugh light smile happy bird flower forest garden sun river", ""));
    }
    var cold = PoetrySelection.select(poems, days("rain", 0), "en", "heartfelt", "middle", "fixture");
    var sunny = PoetrySelection.select(poems, days("clearsky", 20), "en", "heartfelt", "middle", "fixture");
    assertThat(cold.get(0).title()).startsWith("Warm");
    assertThat(sunny.get(0).title()).startsWith("Joy");
    assertThat(cold).allSatisfy(card -> assertThat(poems).contains(card));
    assertThat(new HashSet<>(cold)).hasSize(7);
  }

  @Test void ageLengthAndFunnyMoodAreActualEligibilityRules() {
    var longPoem = poem("Long fixture", "a".repeat(900), "");
    assertThat(PoetrySelection.suitable(longPoem, "en", "heartfelt", "child")).isFalse();
    assertThat(PoetrySelection.suitable(longPoem, "en", "heartfelt", "teen")).isTrue();
    assertThat(PoetrySelection.suitable(poem("Neutral", "A gentle greeting", ""), "en", "funny", "middle")).isFalse();
    assertThat(PoetrySelection.suitable(poem("Comic", "A gentle greeting", "Fables"), "en", "funny", "middle")).isTrue();
  }

  private WikisourcePoems.Poem poem(String title, String text, String categories) {
    return new WikisourcePoems.Poem(title, text, "Fixture author", title, "https://example.test/author", "Q1", 1,
        "https://example.test/source", "https://example.test/license", categories);
  }
  private List<PoetryWeatherService.Day> days(String symbol, double temperature) {
    var result = new ArrayList<PoetryWeatherService.Day>();
    for (int i = 1; i <= 7; i++) result.add(new PoetryWeatherService.Day("2026-11-0" + i, symbol, temperature));
    return result;
  }
}
