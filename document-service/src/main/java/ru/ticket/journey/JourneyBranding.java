package ru.ticket.journey;

import com.fasterxml.jackson.databind.ObjectMapper;
import ru.ticket.common.ApiException;

/** Positions are fractions of the available page area, so images cannot leave the page. */
public record JourneyBranding(JourneyBranding.Placement logo, JourneyBranding.Placement picture) {
  public record Placement(String mediaId, double x, double y, double scale) {}

  private static final ObjectMapper JSON = new ObjectMapper();
  public static final JourneyBranding EMPTY = new JourneyBranding(null, null);

  public static JourneyBranding parse(String value) {
    if (value == null || value.isBlank()) return EMPTY;
    if (value.length() > 2000) throw ApiException.badRequest("journey.media.position");
    try {
      var result = JSON.readValue(value, JourneyBranding.class);
      if (result == null) throw ApiException.badRequest("journey.media.position");
      validate(result);
      return result;
    } catch (java.io.IOException error) {
      throw ApiException.badRequest("journey.media.position");
    }
  }

  public static void validate(JourneyBranding value) {
    if (value == null) return;
    for (var item : new Placement[] {value.logo(), value.picture()}) {
      if (item != null
          && (item.mediaId() == null
              || !item.mediaId().matches("[a-f0-9]{64}")
              || !Double.isFinite(item.x())
              || !Double.isFinite(item.y())
              || !Double.isFinite(item.scale())
              || item.x() < 0
              || item.x() > 1
              || item.y() < 0
              || item.y() > 1
              || item.scale() < .05
              || item.scale() > .6)) throw ApiException.badRequest("journey.media.position");
    }
  }
}
