package ru.ticket.journey;

import java.util.*;

/** Point identities follow their route position; snapshot geometry and immutable data stay intact. */
public final class RoutePointLabels {
  private RoutePointLabels() {}
  private static final Map<String,List<String>> LABELS=Map.of(
      "en",List.of("Route start","Route end","Stop"),
      "ru",List.of("Начало маршрута","Конец маршрута","Остановка"),
      "zh",List.of("路线起点","路线终点","停靠点"),
      "pl",List.of("Początek trasy","Koniec trasy","Przystanek"),
      "it",List.of("Inizio del percorso","Fine del percorso","Tappa"),
      "es",List.of("Inicio de la ruta","Fin de la ruta","Parada"),
      "nl",List.of("Begin van de route","Einde van de route","Stop"),
      "fr",List.of("Début de l’itinéraire","Fin de l’itinéraire","Étape"),
      "de",List.of("Routenstart","Routenende","Stopp"));

  public static String caption(int index,int count,String language,Map<String,String> custom) {
    var labels=LABELS.getOrDefault(language,LABELS.get("en"));
    if(index==0)return custom.getOrDefault("route_start",labels.get(0));
    if(index==count-1)return custom.getOrDefault("route_end",labels.get(1));
    return custom.getOrDefault("stop",labels.get(2))+" "+index;
  }
  public static String marker(int index,int count) {
    return index==0?"A":index==count-1?"B":Integer.toString(index);
  }
}
