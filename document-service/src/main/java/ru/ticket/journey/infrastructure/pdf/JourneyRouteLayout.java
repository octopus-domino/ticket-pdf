package ru.ticket.journey.infrastructure.pdf;

import ru.ticket.journey.application.journey.JourneyService;
import ru.ticket.journey.application.journey.RouteSnapshotService;
import ru.ticket.journey.infrastructure.media.JourneyMediaService;
import ru.ticket.journey.shared.JourneyFieldLabels;
import ru.ticket.journey.domain.model.Trip;
import java.time.*;
import java.time.format.*;
import java.util.*;
import org.springframework.stereotype.Component;
import ru.ticket.dto.*;

/** Dedicated map and itinerary pages are composed once for both SVG preview and PDF. */
@Component
public class JourneyRouteLayout {
  private final RouteSnapshotService routes;
  private final JourneyMediaService media;

  public JourneyRouteLayout(RouteSnapshotService routes, JourneyMediaService media) {
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
      var walk=routes.get(stop.walkId());
      String walkTitle=walk.stops().isEmpty()||walk.stops().get(0).meta()==null?null:walk.stops().get(0).meta().get("walk_title");
      appendSnapshot(pages, view, walk, walkTitle==null?labels.getOrDefault("walk", fallback) + " · " + stop.label():walkTitle);
    }
    document.slides = pages;
  }

  /** Account walks already have saved geometry. Render their maps without retrieving external tiles. */
  public void appendTrip(ExportRequest document, Trip trip) {
    var pages = new ArrayList<>(document.slides);
    int photosRemaining = 24;
    for (var day : trip.days()) {
      var stops = new LinkedHashMap<String, Trip.Segment>();
      day.segments().forEach(stop -> stops.putIfAbsent(stop.id(), stop));
      var groups = new ArrayList<List<Trip.WalkingSection>>();
      for (var section : day.walkingSections()) {
        if (!stops.containsKey(section.fromSegmentId()) || !stops.containsKey(section.toSegmentId())) continue;
        var previous = groups.isEmpty() ? null : groups.get(groups.size() - 1);
        if (previous == null || !Objects.equals(previous.get(previous.size() - 1).toSegmentId(), section.fromSegmentId())) {
          previous = new ArrayList<>();
          groups.add(previous);
        }
        if (previous.size() < 199) previous.add(section);
      }
      for (var group : groups) {
        String title = Objects.toString(day.note(), "City walk");
        var page = tripPage(pages, title, Objects.toString(day.date(), ""));
        drawTripMap(page, group);
        double y = 348;
        var ids = new ArrayList<String>();
        ids.add(group.get(0).fromSegmentId());
        group.forEach(section -> ids.add(section.toSegmentId()));
        for (int index = 0; index < ids.size(); index++) {
          var stop = stops.get(ids.get(index));
          String notes = java.util.stream.Stream.of(stop.note(), stop.walkNote())
              .filter(note -> note != null && !note.isBlank()).collect(java.util.stream.Collectors.joining("\n"));
          boolean hasNote = !notes.isBlank();
          var photo = photosRemaining > 0 ? stop.photoCredits().stream()
              .filter(item -> item != null && Objects.equals(item.stopId(), stop.id())
                  && item.printable() && stop.photoRefs().contains(item.imageId())).findFirst().orElse(null) : null;
          var asset = photo == null ? null : optionalImage(photo.imageId());
          double row = 46 + (hasNote ? 112 : 0) + (asset != null ? 174 : 0);
          if (y + row > 1050) {
            page = tripPage(pages, title, Objects.toString(day.date(), ""));
            y = 104;
          }
          page.elements.add(JourneyTypography.fit((index + 1) + ". " + stop.title(), 50, y, 694, 42, 16, true, "#244e42"));
          y += 46;
          if (hasNote) {
            page.elements.add(JourneyTypography.fit(notes, 50, y, 694, 108, 12, false, "#53665b"));
            y += 112;
          }
          if (asset != null) {
            var image = new ImageElement();
            image.type = "image"; image.id = "photo-" + index;
            image.x = 50; image.y = y;
            double scale = Math.min(280.0 / asset.width(), 120.0 / asset.height());
            image.width = asset.width() * scale; image.height = asset.height() * scale;
            image.src = asset.dataUri();
            page.elements.add(image);
            page.elements.add(JourneyTypography.fit(photo.author() + " · " + photo.license() + "\n"
                + photo.sourceUrl() + "\n" + photo.licenseUrl(), 50, y + 122, 694, 50, 9, false, "#53665b"));
            y += 174;
            photosRemaining--;
          }
        }
      }
    }
    document.slides = pages;
  }

  private ru.ticket.journey.infrastructure.media.JourneyMedia optionalImage(String id) {
    try {
      return media.get(id);
    } catch (ru.ticket.common.ApiException unavailable) {
      return null;
    }
  }

  private static Slide tripPage(List<Slide> pages, String title, String date) {
    var page = new Slide();
    page.id = "trip-walk-" + pages.size();
    page.name = title;
    page.elements.add(JourneyTypography.fit(title, 50, 38, 694, 38, 25, true, "#244e42"));
    page.elements.add(JourneyTypography.fit(date, 50, 76, 694, 24, 12, false, "#53665b"));
    pages.add(page);
    return page;
  }

  private static void drawTripMap(Slide page, List<Trip.WalkingSection> sections) {
    var tracks = new ArrayList<List<Trip.Coordinate>>();
    for (var section : sections) {
      var track = new ArrayList<Trip.Coordinate>();
      int size = section.geometry().size();
      int step = Math.max(1, (size + 99) / 100);
      for (int index = 0; index < size; index += step) track.add(section.geometry().get(index));
      if (size > 1 && (size - 1) % step != 0) track.add(section.geometry().get(size - 1));
      track.removeIf(point -> point == null || !Double.isFinite(point.longitude()) || !Double.isFinite(point.latitude())
          || Math.abs(point.longitude()) > 180 || Math.abs(point.latitude()) > 90);
      if (track.size() > 1) tracks.add(track);
    }
    if (tracks.isEmpty()) return;
    var points = tracks.stream().flatMap(List::stream).toList();
    double west = points.stream().mapToDouble(Trip.Coordinate::longitude).min().orElseThrow();
    double north = points.stream().mapToDouble(Trip.Coordinate::latitude).max().orElseThrow();
    double east = points.stream().mapToDouble(Trip.Coordinate::longitude).max().orElseThrow();
    double south = points.stream().mapToDouble(Trip.Coordinate::latitude).min().orElseThrow();
    double longitudeScale = Math.max(0.08, Math.cos(Math.toRadians(north)));
    double scale = Math.min(650 / Math.max((east - west) * longitudeScale, 1e-6), 185 / Math.max(north - south, 1e-6));
    var path = new PathElement();
    path.id = "walking-geometry"; path.type = "path"; path.x = 60; path.y = 124;
    path.width = 670; path.height = 202; path.fill = "none"; path.stroke = "#317559"; path.strokeWidth = 4;
    var data = new StringBuilder();
    var markers = new ArrayList<Trip.Coordinate>();
    markers.add(tracks.get(0).get(0));
    for (var track : tracks) {
      for (int index = 0; index < track.size(); index++) {
        var point = track.get(index);
        data.append(index == 0 ? "M " : "L ").append(Math.round((point.longitude() - west) * longitudeScale * scale))
            .append(' ').append(Math.round((north - point.latitude()) * scale)).append(' ');
      }
      markers.add(track.get(track.size() - 1));
    }
    path.d = data.toString();
    page.elements.add(path);
    for (int index = 0; index < markers.size(); index++) {
      var marker = markers.get(index);
      page.elements.add(JourneyTypography.fit(Integer.toString(index + 1),
          61 + (marker.longitude() - west) * longitudeScale * scale,
          115 + (north - marker.latitude()) * scale, 28, 20, 11, true, "#244e42"));
    }
  }

  private void appendSnapshot(List<Slide> pages, JourneyService.View view, RouteSnapshotService.Snapshot snapshot, String title) {
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
    image.height = 694.0 * asset.height() / asset.width();
    image.src = asset.dataUri();
    page.elements.add(image);
    double y = 140 + image.height;
    Locale locale = Locale.forLanguageTag(view.data().getOrDefault("locale", "en"));
    DateTimeFormatter date =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale);
    var captions = routeLabels(view.data().get("route_labels"));
    var licenses=new LinkedHashMap<String,String>();
    if (snapshot.photos() != null) for (var photo : snapshot.photos()) {
      String number = "";
      for (int index = 0; index < snapshot.stops().size(); index++)
        if (snapshot.stops().get(index).id().equals(photo.stopId())) number = "interior".equals(snapshot.pointNumbering())
            ? RoutePrintLabels.pointMarker(index,snapshot.stops().size()) : Integer.toString(index+1);
      String credit = (number + ". " + photo.author() + " · " + photo.license() + " · " + photo.sourceUrl()).replaceAll("\\s+"," ").strip();
      var text=JourneyTypography.fit(credit,50,y,694,900,8,false,"#53665b");
      text.lineHeight=1.1;text.height=Math.ceil(text.text.split("\n",-1).length*8*1.1)+2;
      if(y+text.height>1050){page=page(view,pages.size());pages.add(page);y=50;}
      text.y=y;page.elements.add(text);y+=text.height+2;
      licenses.put(photo.licenseUrl(),photo.license());
    }
    for(var license:licenses.entrySet()){
      var text=JourneyTypography.fit((license.getValue()+" · "+license.getKey()).replaceAll("\\s+"," "),50,y,694,900,8,false,"#53665b");
      text.lineHeight=1.1;text.height=Math.ceil(text.text.split("\n",-1).length*8*1.1)+2;
      if(y+text.height>1050){page=page(view,pages.size());pages.add(page);y=50;}
      text.y=y;page.elements.add(text);y+=text.height+2;
    }
    y+=8;
    int stopNumber = 0;
    for (var row : snapshot.timeline()) {
      var stop="stop".equals(row.kind())&&stopNumber<snapshot.stops().size()?snapshot.stops().get(stopNumber):null;
      var badges=new ArrayList<String>();
      if(stop!=null&&stop.meta()!=null&&stop.meta().containsKey("theme")){
        var meta=stop.meta();
        if("wikimapia".equals(meta.get("source"))&&"false".equals(meta.get("in_osm")))badges.add(RoutePrintLabels.badge(locale.getLanguage(),0));
        if("CLOSED".equals(meta.get("visit_status")))badges.add(RoutePrintLabels.badge(locale.getLanguage(),1));
        if("UNKNOWN".equals(meta.get("visit_status")))badges.add(RoutePrintLabels.badge(locale.getLanguage(),2));
      }
      double badgeHeight=badges.isEmpty()?0:28;
      String description = Objects.toString(row.description(), "");
      String note = stop == null || stop.meta() == null ? null : stop.meta().get("user_note");
      if (note != null && !note.isBlank()) description = description.isBlank() ? note : description + "\n" + note;
      boolean hasNotes = !description.isBlank();
      double rowHeight = (hasNotes ? 200 : 88)+badgeHeight;
      if (y + rowHeight > 1050) {
        page = page(view, pages.size());
        pages.add(page);
        y = 50;
      }
      String startZone = row.startZone() == null ? row.zone() : row.startZone();
      String start = date.format(Instant.parse(row.start()).atZone(ZoneId.of(startZone)));
      String end = date.format(Instant.parse(row.end()).atZone(ZoneId.of(row.zone())));
      String caption="stop".equals(row.kind())
          ? RoutePrintLabels.pointCaption(stopNumber++,snapshot.stops().size(),locale.getLanguage(),captions)
          : captions.getOrDefault(row.kind(),row.kind());
      page.elements.add(
          JourneyTypography.fit(
              caption + " · " + row.label(),
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
      if(!badges.isEmpty())page.elements.add(JourneyTypography.fit(String.join(" · ",badges),50,y+76,694,24,11,false,"#9CA3AF"));
      if(stop!=null&&stop.meta()!=null&&stop.meta().containsKey("theme")&&snapshot.photos()!=null){
        final String stopId=stop.id();
        if(snapshot.photos().stream().anyMatch(photo->photo.stopId().equals(stopId))){
          var camera=new ShapeElement();camera.id="camera-"+stop.id();camera.type="shape";camera.shape="rect";camera.x=724;camera.y=y+4;camera.width=16;camera.height=12;camera.fill="#53665b";page.elements.add(camera);
          var lens=new ShapeElement();lens.id="camera-lens-"+stop.id();lens.type="shape";lens.shape="ellipse";lens.x=729;lens.y=y+7;lens.width=6;lens.height=6;lens.fill="#ffffff";page.elements.add(lens);
        }
      }
      if (hasNotes) page.elements.add(JourneyTypography.fit(description, 50, y + 78+badgeHeight, 694, 108, 13, false, "#53665b"));
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
