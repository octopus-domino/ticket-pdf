package ru.ticket.journey.infrastructure.pdf;

import java.util.List;
import java.util.Map;

/** Captions, markers and badges for the printed itinerary. */
final class RoutePrintLabels {
  private RoutePrintLabels() {}

  private static final Map<String,List<String>> BADGE_LABELS=Map.of(
      "en",List.of("Community place","Closed according to OSM","Opening hours not provided"),
      "ru",List.of("Народное место","Закрыто по данным OSM","Часы работы не указаны"),
      "zh",List.of("社区发现的地点","据OSM显示已关闭","未提供开放时间"),
      "pl",List.of("Miejsce społeczności","Zamknięte według OSM","Brak godzin otwarcia"),
      "it",List.of("Luogo della comunità","Chiuso secondo OSM","Orari non indicati"),
      "es",List.of("Lugar de la comunidad","Cerrado según OSM","Horario no indicado"),
      "nl",List.of("Plek van de gemeenschap","Gesloten volgens OSM","Openingstijden ontbreken"),
      "fr",List.of("Lieu de la communauté","Fermé selon OSM","Horaires non renseignés"),
      "de",List.of("Ort der Gemeinschaft","Laut OSM geschlossen","Öffnungszeiten fehlen"));
  private static final Map<String,List<String>> POINT_LABELS=Map.of(
      "en",List.of("Route start","Route end","Stop"),
      "ru",List.of("Начало маршрута","Конец маршрута","Остановка"),
      "zh",List.of("路线起点","路线终点","停靠点"),
      "pl",List.of("Początek trasy","Koniec trasy","Przystanek"),
      "it",List.of("Inizio del percorso","Fine del percorso","Tappa"),
      "es",List.of("Inicio de la ruta","Fin de la ruta","Parada"),
      "nl",List.of("Begin van de route","Einde van de route","Stop"),
      "fr",List.of("Début de l’itinéraire","Fin de l’itinéraire","Étape"),
      "de",List.of("Routenstart","Routenende","Stopp"));

  static String badge(String language, int index) {
    return BADGE_LABELS.getOrDefault(language, BADGE_LABELS.get("en")).get(index);
  }

  static String pointCaption(int index, int count, String language, Map<String, String> custom) {
    var labels = POINT_LABELS.getOrDefault(language, POINT_LABELS.get("en"));
    if (index == 0) return custom.getOrDefault("route_start", labels.get(0));
    if (index == count - 1) return custom.getOrDefault("route_end", labels.get(1));
    return custom.getOrDefault("stop", labels.get(2)) + " " + index;
  }

  static String pointMarker(int index, int count) {
    return index == 0 ? "A" : index == count - 1 ? "B" : Integer.toString(index);
  }
}
