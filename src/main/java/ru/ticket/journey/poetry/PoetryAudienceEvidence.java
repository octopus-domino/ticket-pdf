package ru.ticket.journey.poetry;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/** Reads explicit genre labels in publisher metadata, never guesses age from words in a poem. */
final class PoetryAudienceEvidence {
  private PoetryAudienceEvidence() {}
  private static final Map<String,String> CHILD_GENRES=Map.of(
      "ru","детск[а-яё]*\\s+(?:поэзи|стих|сказ|песен|литератур)|для\\s+детей|колыбельн|потешк|прибаутк|считалк",
      "en","children['’]?s?\\s+(?:poem|poetry|verse|literature)|for children|nursery rhyme|lullab",
      "zh","儿童诗|兒童詩|童谣|童謠|儿歌|兒歌|摇篮曲|搖籃曲",
      "pl","wiersz[ey]? dla dzieci|poezja dziecięca|kołysank|rymowank",
      "it","poesi[ae] per bambini|poesia infantile|filastrocc|ninna nanna",
      "es","poesía infantil|poemas? para niños|canción de cuna|canciones infantiles",
      "nl","kinderpoëzie|kindergedicht|kinderlied|wiegelied",
      "fr","poésie enfantine|poèmes? pour enfants|comptine|berceuse",
      "de","kindergedicht|kinderlyrik|kinderreim|kinderlied|wiegenlied");

  static boolean childrenGenre(String editorial,String language) {
    return editorial!=null&&Pattern.compile(CHILD_GENRES.getOrDefault(language,CHILD_GENRES.get("en")),Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE)
        .matcher(editorial).find();
  }
  static final class Mismatch extends IOException {
    Mismatch(){super("Publisher classifies this work as children's poetry");}
  }
}
