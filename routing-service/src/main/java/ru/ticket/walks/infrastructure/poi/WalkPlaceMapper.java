package ru.ticket.walks.infrastructure.poi;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import ru.ticket.walks.application.model.WalkPlace;

public final class WalkPlaceMapper {
  private WalkPlaceMapper() {}

  public static WalkPlace themedPlace(
          WalkThemeService.WalkCandidate point, String theme, ObjectMapper mapper) {
    var meta = new LinkedHashMap<String, String>();
    meta.put("source", point.source());
    meta.put("theme", theme);
    meta.put("in_osm", Boolean.toString(point.inOsm()));
    meta.put("visit_status", point.visitStatus());
    if (!point.openingHours().isBlank()) meta.put("opening_hours", point.openingHours());
    if (!point.wikidataId().isBlank()) meta.put("wikidata", point.wikidataId());
    if (point.wikidataSitelinks() != null) meta.put("wikidata_sitelinks", point.wikidataSitelinks().toString());
    if (!point.wikimapiaId().isBlank()) meta.put("wikimapia_id", point.wikimapiaId());
    if (!point.description().isBlank())
      meta.put(point.source().equals("mystic")?"place_description":"wikimapia_description", point.description().substring(0, Math.min(500, point.description().length())));
    String url = point.source().equals("mystic") ? point.sourceUrl() : point.source().equals("wikimapia")
            ? "https://wikimapia.org/" + point.wikimapiaId()
            : "https://www.openstreetmap.org/" + point.id().replaceFirst("-", "/");
    return new WalkPlace(point.id(), point.label(), point.lon(), point.lat(), url,
            OsmOpeningHours.parse(point.openingHours(), mapper), point.openingHours(), null,
            point.visitStatus(), point.wikidataSitelinks(), point.inOsm(), point.source(),
            point.wikimapiaId(), point.description(), Map.copyOf(meta), point.score());
  }

}
