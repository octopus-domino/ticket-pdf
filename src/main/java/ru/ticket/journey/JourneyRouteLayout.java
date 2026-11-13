package ru.ticket.journey;

import java.time.*;
import java.time.format.*;
import java.util.*;
import org.springframework.stereotype.Component;
import ru.ticket.dto.*;

/** Dedicated map and itinerary pages are composed once for both SVG preview and PDF. */
@Component
public class JourneyRouteLayout {
  private final JourneyRouteService routes;
  private final JourneyMediaService media;

  public JourneyRouteLayout(JourneyRouteService routes, JourneyMediaService media) {
    this.routes = routes;
    this.media = media;
  }

  public void append(ExportRequest document, JourneyService.View view) {
    String id = view.data().get("route");
    if (id == null || id.isBlank()) return;
    var snapshot = routes.get(id);
    List<Slide> pages = new ArrayList<>(document.slides);
    appendSnapshot(pages, view, snapshot, view.title());
    for (var stop : snapshot.stops()) if (stop.walkId() != null) {
      var labels = routeLabels(view.data().get("route_labels"));
      String fallback = "ru".equals(view.data().get("locale")) ? "Прогулка" : "City walk";
      appendSnapshot(pages, view, routes.get(stop.walkId()), labels.getOrDefault("walk", fallback) + " · " + stop.label());
    }
    document.slides = pages;
  }

  private void appendSnapshot(List<Slide> pages, JourneyService.View view, JourneyRouteService.Snapshot snapshot, String title) {
    var asset = media.get(snapshot.mapId());
    Slide page = page(view, pages.size());
    pages.add(page);
    page.elements.add(JourneyTypography.fit(title, 50, 40, 694, 65, 26, true, "#244e42"));
    var image = new ImageElement();
    image.type = "image";
    image.id = "route-map";
    image.x = 50;
    image.y = 120;
    image.width = 694;
    image.height = 694.0 * asset.height / asset.width;
    image.src =
        "data:"
            + asset.contentType
            + ";base64,"
            + Base64.getEncoder().encodeToString(asset.content);
    page.elements.add(image);
    double y = 140 + image.height;
    Locale locale = Locale.forLanguageTag(view.data().getOrDefault("locale", "en"));
    DateTimeFormatter date =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale);
    var captions = routeLabels(view.data().get("route_labels"));
    if (snapshot.photos() != null) for (var photo : snapshot.photos()) {
      if (y + 150 > 1050) { page = page(view, pages.size()); pages.add(page); y = 50; }
      int number = 0;
      for (int index = 0; index < snapshot.stops().size(); index++)
        if (snapshot.stops().get(index).id().equals(photo.stopId())) number = index + 1;
      String credit = number + ". " + photo.author() + " · " + photo.license() + "\n"
          + photo.sourceUrl() + "\n" + photo.licenseUrl();
      page.elements.add(JourneyTypography.fit(credit, 50, y, 694, 138, 12, false, "#53665b"));
      y += 150;
    }
    int stopNumber = 0;
    for (var row : snapshot.timeline()) {
      boolean hasNotes = row.description() != null && !row.description().isBlank();
      double rowHeight = hasNotes ? 200 : 88;
      if (y + rowHeight > 1050) {
        page = page(view, pages.size());
        pages.add(page);
        y = 50;
      }
      String startZone = row.startZone() == null ? row.zone() : row.startZone();
      String start = date.format(Instant.parse(row.start()).atZone(ZoneId.of(startZone)));
      String end = date.format(Instant.parse(row.end()).atZone(ZoneId.of(row.zone())));
      page.elements.add(
          JourneyTypography.fit(
              ("stop".equals(row.kind()) ? ++stopNumber + ". " : "")
                  + captions.getOrDefault("stop".equals(row.kind()) && stopNumber == 1 ? "departure" : row.kind(), row.kind()) + " · " + row.label(),
              50,
              y,
              694,
              42,
              15,
              true,
              "#244e42"));
      page.elements.add(
          JourneyTypography.fit(
              start
                  + (row.start().equals(row.end()) ? "" : " — " + end)
                  + " · "
                  + (startZone.equals(row.zone()) ? row.zone() : startZone + " → " + row.zone()),
              50,
              y + 44,
              694,
              28,
              12,
              false,
              "#53665b"));
      if (hasNotes) page.elements.add(JourneyTypography.fit(row.description(), 50, y + 78, 694, 108, 13, false, "#53665b"));
      y += rowHeight;
    }
  }

  private static Map<String, String> routeLabels(String raw) {
    if (raw == null) return Map.of();
    return JourneyFieldLabels.read(raw);
  }

  private static Slide page(JourneyService.View view, int index) {
    var page = new Slide();
    page.id = view.id() + "-route-" + index;
    page.name = view.title();
    page.elements.add(
        JourneyTypography.fit(
            "© OpenStreetMap contributors · https://www.openstreetmap.org/copyright",
            50,
            1080,
            694,
            22,
            10,
            false,
            "#53665b"));
    return page;
  }
}
