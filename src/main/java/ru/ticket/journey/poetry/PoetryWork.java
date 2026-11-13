package ru.ticket.journey.poetry;

import java.util.Map;

/** Full text and provenance; provider excerpts cannot silently become complete cards. */
public record PoetryWork(String title,String text,String author,String authorId,String authorUrl,
    String country,String sourceUrl,String licenseUrl,Map<String,Object> metadata) {
  boolean suitableFallback(PoetryQuery query) {
    return PoetrySelection.suitable(new WikisourcePoems.Poem(title,text,author,authorId,authorUrl,country,0,sourceUrl,licenseUrl,""),query.language(),query.mood(),query.audience());
  }
  static PoetryWork wiki(WikisourcePoems.Poem poem,String language,String mood) {
    return new PoetryWork(poem.title(),poem.text(),poem.author(),poem.authorId(),poem.authorUrl(),poem.country(),poem.sourceUrl(),poem.licenseUrl(),
        Map.of("source","wikisource","language",language,"mood",mood,"text_complete",true));
  }
}
