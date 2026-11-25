package ru.ticket.walks.infrastructure.poi;

import java.io.IOException;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.ticket.walks.domain.service.PoiSource;
import ru.ticket.walks.infrastructure.mcp.OsmMcpClient;

/** Spatial subdivision avoids turning the upstream page size into a limit on a walk's places. */
@Component
public class PhotonAreaPoiSource implements PoiSource<OsmMcpClient.Poi, WalkThemeProperties.Theme> {
  private static final int PAGE_SIZE = 50;
  private static final int MAX_DEPTH = 5;
  private static final double METERS_PER_DEGREE = 111195;
  private final WalkRemote remote;
  private final String endpoint;
  private record Box(double west, double south, double east, double north, int depth) {}

  public PhotonAreaPoiSource(WalkRemote remote,
      @Value("${app.journeys.photon-url:https://photon.komoot.io/api/}") String endpoint) {
    this.remote = remote; this.endpoint = endpoint;
  }

  /** Fetches all selected categories together and keeps actual OSM tags for classification. */
  @Override
  public List<OsmMcpClient.Poi> searchArea(double lon, double lat, int radius, WalkThemeProperties.Theme theme, String language) throws IOException {
    double dy = radius / METERS_PER_DEGREE, dx = dy / Math.cos(Math.toRadians(lat));
    var boxes = new ArrayDeque<Box>();
    boxes.add(new Box(lon - dx, lat - dy, lon + dx, lat + dy, 0));
    var found = new LinkedHashMap<String, OsmMcpClient.Poi>();
    String include = String.join(",", theme.tags().stream().map(tag -> "osm." + tag.replace("=*", "").replace('=', '.')).distinct().toList());
    while (!boxes.isEmpty()) {
      if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Area search cancelled");
      var box = boxes.removeFirst();
      var features = remote.get(url(box, include, language)).path("features");
      if (!features.isArray()) throw new IOException("Invalid area search response");
      features.forEach(feature -> add(found, PhotonPoiSource.parse(feature), lon, lat, radius));
      if (features.size() >= PAGE_SIZE && box.depth() < MAX_DEPTH) split(box, boxes);
    }
    return List.copyOf(found.values());
  }

  private String url(Box box, String include, String language) {
    String bbox = String.format(Locale.ROOT, "%.7f,%.7f,%.7f,%.7f", box.west(), box.south(), box.east(), box.north());
    String lang = Set.of("en", "de", "fr").contains(language) ? "&lang=" + language : "";
    return endpoint + "?limit=" + PAGE_SIZE + "&bbox=" + WalkRemote.encode(bbox) + "&include=" + WalkRemote.encode(include) + lang;
  }

  private void add(Map<String, OsmMcpClient.Poi> found, OsmMcpClient.Poi point, double lon, double lat, int radius) {
    if (point != null && WikimapiaPoiSourceImpl.distance(lon, lat, point.lon(), point.lat()) <= radius)
      found.putIfAbsent(point.id(), point);
  }

  private void split(Box box, Deque<Box> queue) {
    double x = (box.west() + box.east()) / 2, y = (box.south() + box.north()) / 2;
    int depth = box.depth() + 1;
    queue.add(new Box(box.west(), box.south(), x, y, depth));
    queue.add(new Box(x, box.south(), box.east(), y, depth));
    queue.add(new Box(box.west(), y, x, box.north(), depth));
    queue.add(new Box(x, y, box.east(), box.north(), depth));
  }
}
