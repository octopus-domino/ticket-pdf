package ru.ticket.journey.walk;

import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import ru.ticket.common.ApiException;

/** Themes are operator configuration; request text is never interpolated into Overpass QL. */
@Component
@ConfigurationProperties(prefix = "app.journeys")
public class WalkThemeProperties {
  private Map<String, Theme> walkThemes = new LinkedHashMap<>();
  public record Theme(List<String> tags, List<String> wikimapiaCategories,
                      List<String> descriptionKeywords, boolean requireHours) {
    public Theme {
      tags = tags == null ? List.of() : List.copyOf(tags);
      wikimapiaCategories = wikimapiaCategories == null ? List.of() : List.copyOf(wikimapiaCategories);
      descriptionKeywords = descriptionKeywords == null ? List.of() : List.copyOf(descriptionKeywords);
    }
  }
  public Map<String, Theme> getWalkThemes() { return walkThemes; }
  public void setWalkThemes(Map<String, Theme> themes) { walkThemes = new LinkedHashMap<>(themes); }
  public Theme require(String id) {
    var theme = walkThemes.get(id);
    if (theme == null) throw ApiException.badRequest("journey.route.invalid-route");
    return theme;
  }
}
