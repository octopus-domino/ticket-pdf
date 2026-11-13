package ru.ticket.journey.poetry;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PoetryDisplayTextTest {
  @Test void repairsOnlyLegacyMarginNumbersAtTheirActualVersePositions(){
    String text="First\nSecond\nThird\nFourth\n5Fifth\n\nSixth\nSeventh\nEighth\nNinth\n10Tenth\n2 May 1916";
    assertEquals(text.replace("5Fifth","Fifth").replace("10Tenth","Tenth"),PoetryDisplayText.of(text,"https://ru.wikisource.org/wiki/Fixture"));
    assertEquals(text,PoetryDisplayText.of(text,"https://poemship.com/poems/fixture"));
  }
  @Test void preservesAuthorNumbersAndDates(){
    String text="First\nSecond\nThird\nFourth\n5 years later\n\n2 May 1916";
    assertEquals(text,PoetryDisplayText.of(text,"https://en.wikisource.org/wiki/Fixture"));
  }
}
