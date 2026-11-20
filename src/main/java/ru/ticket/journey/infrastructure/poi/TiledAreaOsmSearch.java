package ru.ticket.journey.infrastructure.poi;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Component;
import ru.ticket.mcp.OsmMcpClient;

/** Stable OSM cells shared by overlapping area requests. The area coordinator serializes provider work. */
@Component
public class TiledAreaOsmSearch {
  private static final double LAT_STEP = .0125;
  private static final double LON_STEP = .025;
  private static final int COLUMNS = 14400;
  private static final int MAX_FETCHES = 8;
  private static final Duration SEARCH_TIME = Duration.ofSeconds(14);
  private static final Duration CELL_TIME = Duration.ofSeconds(4);
  private record Cell(int row, int column, int depth) {}
  private record Key(Cell cell, List<String> tags, String language) {}
  private record Bounds(double south, double west, double north, double east) {}
  record Coverage(List<OsmMcpClient.Poi> points, boolean complete, boolean available) {}
  private final OsmPoiSource osm;
  private final WalkCache<Key, List<OsmMcpClient.Poi>> cells = new WalkCache<>(256, Duration.ofMinutes(10));
  private final WalkCache<Key, Boolean> split = new WalkCache<>(128, Duration.ofMinutes(10));
  private final WalkCache<Key, Boolean> retryLater = new WalkCache<>(128, Duration.ofSeconds(15));

  public TiledAreaOsmSearch(OsmPoiSource osm) { this.osm = osm; }

  Coverage search(double lon, double lat, int radius, WalkThemeProperties.Theme theme, String language) {
    double dy = radius / 111000.0;
    double dx = dy / Math.cos(Math.toRadians(Math.abs(lat) + dy));
    var grid = new ArrayList<Cell>();
    for (int row = (int) Math.floor((lat - dy) / LAT_STEP); row <= Math.floor((lat + dy) / LAT_STEP); row++) {
      for (int column = (int) Math.floor((lon - dx + 180) / LON_STEP);
           column <= Math.floor((lon + dx + 180) / LON_STEP); column++) {
        var cell = new Cell(row, Math.floorMod(column, COLUMNS), 0);
        if (intersects(bounds(cell), lon, lat, radius)) grid.add(cell);
      }
    }
    grid.sort(Comparator.comparingDouble(cell -> {
      var box = bounds(cell);
      return WikimapiaPoiSourceImpl.distance(lon, lat, (box.west + box.east) / 2, (box.south + box.north) / 2);
    }));
    var pending = new ArrayDeque<>(grid);
    var points = new LinkedHashMap<String, OsmMcpClient.Poi>();
    var tags = theme.tags().stream().distinct().sorted().toList();
    int remaining = MAX_FETCHES;
    long deadline = System.nanoTime() + SEARCH_TIME.toNanos();
    boolean complete = true, available = false;
    while (!pending.isEmpty()) {
      Cell cell = pending.removeFirst();
      Bounds bounds = bounds(cell);
      if (!intersects(bounds, lon, lat, radius)) continue;
      var key = new Key(cell, tags, language);
      if (split.get(key) != null) {
        for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++)
          pending.addLast(new Cell(cell.row * 2 + y, cell.column * 2 + x, cell.depth + 1));
        continue;
      }
      var result = cells.get(key);
      if (result == null && remaining > 0 && System.nanoTime() < deadline && retryLater.get(key) == null
          && !Thread.currentThread().isInterrupted()) {
        remaining--;
        try (var budget = WalkBudget.until(Math.min(deadline, System.nanoTime() + CELL_TIME.toNanos()))) {
          result = osm.searchBox(bounds.south, bounds.west, bounds.north, bounds.east, tags, language);
          cells.put(key, result);
        } catch (IOException unavailable) {
          if (unavailable instanceof OsmPoiSource.DenseCell && cell.depth < 2) {
            split.put(key, true);
            pending.addFirst(cell);
            continue;
          }
          retryLater.put(key, true);
        }
      }
      if (result == null) {
        complete = false;
        continue;
      }
      available = true;
      for (var point : result) {
        if (WikimapiaPoiSourceImpl.distance(lon, lat, point.lon(), point.lat()) <= radius)
          points.putIfAbsent(point.id(), point);
      }
    }
    return new Coverage(List.copyOf(points.values()), complete, available);
  }

  private static Bounds bounds(Cell cell) {
    int scale = 1 << cell.depth;
    double south = cell.row * LAT_STEP / scale, west = cell.column * LON_STEP / scale - 180;
    return new Bounds(south, west, south + LAT_STEP / scale, west + LON_STEP / scale);
  }

  private static boolean intersects(Bounds box, double lon, double lat, int radius) {
    double midpoint = (box.west + box.east) / 2;
    double nearLon = lon + 360 * Math.rint((midpoint - lon) / 360);
    double x = Math.max(box.west, Math.min(box.east, nearLon));
    double y = Math.max(box.south, Math.min(box.north, lat));
    return WikimapiaPoiSourceImpl.distance(lon, lat, x, y) <= radius + 10;
  }
}
