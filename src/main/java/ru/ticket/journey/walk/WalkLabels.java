package ru.ticket.journey.walk;

import java.util.*;

/** Print labels also work when a route is submitted directly through the public API. */
public final class WalkLabels {
  private WalkLabels() {}
  private static final Map<String,List<String>> LABELS=Map.of(
      "en",List.of("Community place","Closed according to OSM","Opening hours not provided"),
      "ru",List.of("Народное место","Закрыто по данным OSM","Часы работы не указаны"),
      "zh",List.of("社区发现的地点","据OSM显示已关闭","未提供开放时间"),
      "pl",List.of("Miejsce społeczności","Zamknięte według OSM","Brak godzin otwarcia"),
      "it",List.of("Luogo della comunità","Chiuso secondo OSM","Orari non indicati"),
      "es",List.of("Lugar de la comunidad","Cerrado según OSM","Horario no indicado"),
      "nl",List.of("Plek van de gemeenschap","Gesloten volgens OSM","Openingstijden ontbreken"),
      "fr",List.of("Lieu de la communauté","Fermé selon OSM","Horaires non renseignés"),
      "de",List.of("Ort der Gemeinschaft","Laut OSM geschlossen","Öffnungszeiten fehlen"));
  public static String text(String language,int index){return LABELS.getOrDefault(language,LABELS.get("en")).get(index);}
}
