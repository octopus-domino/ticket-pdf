package ru.ticket.journey.infrastructure.poi;

import java.util.*;
import ru.ticket.common.ApiException;

/** Strategy selection: only operator-defined selectors are combined into one provider query. */
public record AreaThemeSelection(Map<String, WalkThemeProperties.Theme> themes) {
  private static final WalkThemeProperties.Theme ATTRACTIONS = new WalkThemeProperties.Theme(
      List.of("tourism=attraction", "tourism=museum", "tourism=gallery", "tourism=viewpoint", "historic=monument", "historic=memorial"),
      List.of("attraction", "museum", "monument", "достопримечательност", "музей", "памятник"), List.of(), false);

  /** Canonical order makes cache identity independent of checkbox order. */
  public static AreaThemeSelection resolve(List<String> requested, WalkThemeProperties properties) {
    if (requested == null || requested.isEmpty() || requested.size() > properties.getWalkThemes().size() + 1)
      throw ApiException.badRequest("journey.route.invalid-route");
    var selected = new LinkedHashMap<String, WalkThemeProperties.Theme>();
    requested.stream().map(String::trim).distinct().sorted().forEach(id ->
        selected.put(id, id.equals("attractions") ? ATTRACTIONS : properties.require(id)));
    return new AreaThemeSelection(Collections.unmodifiableMap(selected));
  }

  /** Mystic places come from the curated local catalogue, not from broad OSM tags. */
  public Map<String, WalkThemeProperties.Theme> publicThemes() {
    var result = new LinkedHashMap<>(themes);
    result.remove("mystic");
    return result;
  }

  public WalkThemeProperties.Theme combined() {
    var values = publicThemes().values();
    return new WalkThemeProperties.Theme(values.stream().flatMap(theme -> theme.tags().stream()).distinct().toList(),
        values.stream().flatMap(theme -> theme.wikimapiaCategories().stream()).distinct().toList(),
        values.stream().flatMap(theme -> theme.descriptionKeywords().stream()).distinct().toList(), false);
  }

  public List<String> matchingTags(Map<String, String> tags) {
    return publicThemes().entrySet().stream().filter(entry -> OsmPoiSource.matches(tags, entry.getValue())).map(Map.Entry::getKey).toList();
  }

  public List<String> matchingCategories(List<String> categories, String description) {
    return publicThemes().entrySet().stream().filter(entry ->
        WikimapiaPoiSourceImpl.matches(categories, entry.getValue().wikimapiaCategories())
        || !description.isBlank() && WikimapiaPoiSourceImpl.matches(List.of(description), entry.getValue().descriptionKeywords()))
        .map(Map.Entry::getKey).toList();
  }
}
