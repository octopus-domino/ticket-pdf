package ru.ticket.journey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WikimapiaKeyProvider {
  private final String key;
  public WikimapiaKeyProvider(@Value("${app.journeys.wikimapia-key:}") String key){this.key=key;}
  public boolean enabled(){return !key.isBlank();}
  public String key(){return key;}
}
