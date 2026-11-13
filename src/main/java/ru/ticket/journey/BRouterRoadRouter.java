package ru.ticket.journey;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.ticket.common.ApiException;

/** An ephemeral, single-thread router with a bounded on-disk graph cache, never a resident JVM. */
@Service
public class BRouterRoadRouter implements RoadRouter {
  private static final long MAX_CACHE = 4L * 1024 * 1024 * 1024;
  private static final long MAX_TILE = 256L * 1024 * 1024;
  private final Path home, cache;
  private final ObjectMapper mapper;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

  public BRouterRoadRouter(
      ObjectMapper mapper,
      @Value("${app.journeys.router-home:/opt/brouter}") String home,
      @Value("${app.journeys.router-cache:/app/data/routing}") String cache) {
    this.mapper = mapper;
    this.home = Path.of(home);
    this.cache = Path.of(cache);
  }

  @Override
  public synchronized Road route(Point start, Point end, String mode) {
    validate(start);
    validate(end);
    if (mode == null || !Set.of("car", "foot").contains(mode))
      throw ApiException.badRequest("journey.route.invalid-route");
    if (distance(start, end) > (mode.equals("car") ? 1500000 : 400000))
      throw ApiException.badRequest(mode.equals("car") ? "journey.route.too-long" : "journey.route.walk-too-long");
    Process process = null;
    try {
      if (!Files.isRegularFile(home.resolve("brouter.jar")))
        throw new IOException("Router unavailable");
      Files.createDirectories(cache.resolve("segments4"));
      Files.createDirectories(cache.resolve("custom"));
      var tiles = tiles(start, end);
      long downloadDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(180);
      for (String tile : tiles) ensureTile(tile, tiles, downloadDeadline);
      // Use a dynamically allocated loopback port; only one shared heavy-operation permit is held.
      int port;
      try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
        port = socket.getLocalPort();
      }
      String javaBinary = Path.of(System.getProperty("java.home"), "bin", "java").toString();
      var command = new ArrayList<String>();
      // A parent JVM crash must not leave an idle routing JVM behind in the container.
      if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux"))
        Collections.addAll(command, "timeout", "105");
      Collections.addAll(
          command,
          javaBinary,
          "-Xms16m",
          "-Xmx256m",
          "-XX:MaxMetaspaceSize=64m",
          "-XX:MaxDirectMemorySize=32m",
          "-XX:ActiveProcessorCount=1",
          "-DmaxRunningTime=90",
          "-cp",
          home.resolve("brouter.jar").toString(),
          "btools.server.RouteServer",
          cache.resolve("segments4").toString(),
          home.resolve("profiles2").toString(),
          cache.resolve("custom").toString(),
          Integer.toString(port),
          "1",
          "127.0.0.1");
      process =
          new ProcessBuilder(command)
              .redirectOutput(ProcessBuilder.Redirect.DISCARD)
              .redirectError(ProcessBuilder.Redirect.DISCARD)
              .start();
      boolean ready = false;
      for (int i = 0; i < 50 && process.isAlive(); i++) {
        try (Socket socket = new Socket()) {
          socket.connect(new InetSocketAddress("127.0.0.1", port), 100);
          ready = true;
          break;
        } catch (IOException ex) {
          Thread.sleep(100);
        }
      }
      if (!ready) throw new IOException("Router did not start");
      String profile = mode.equals("car") ? "car-fast" : "codoc-foot";
      String points =
          start.longitude() + "," + start.latitude() + "|" + end.longitude() + "," + end.latitude();
      URI uri =
          URI.create(
              "http://127.0.0.1:"
                  + port
                  + "/brouter?lonlats="
                  + URLEncoder.encode(points, java.nio.charset.StandardCharsets.UTF_8)
                  + "&profile="
                  + profile
                  + "&alternativeidx=0&format=geojson");
      var response =
          http.send(
              HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(95)).GET().build(),
              HttpResponse.BodyHandlers.ofInputStream());
      byte[] bytes;
      try (var stream = response.body()) {
        bytes = stream.readNBytes(2000001);
      }
      if (response.statusCode() != 200 || bytes.length > 2000000)
        throw new IOException("No road route");
      var feature = mapper.readTree(bytes).path("features").path(0);
      var geometry = feature.path("geometry");
      if (!geometry.path("type").asText().equals("LineString"))
        throw new IOException("Invalid route geometry");
      List<List<Double>> coordinates = new ArrayList<>();
      for (var coordinate : geometry.path("coordinates")) {
        double lon = coordinate.path(0).asDouble(Double.NaN),
            lat = coordinate.path(1).asDouble(Double.NaN);
        validate(new Point(lon, lat));
        coordinates.add(List.of(lon, lat));
        if (coordinates.size() > 30000) throw new IOException("Route geometry too large");
      }
      var properties = feature.path("properties");
      double length = properties.path("track-length").asDouble(0);
      double duration =
          mode.equals("foot") ? length / 1.25 : properties.path("total-time").asDouble(0);
      if (coordinates.size() < 2 || length <= 0 || duration <= 0 || duration > 604800)
        throw new IOException("Invalid route measurements");
      return new Road(List.copyOf(coordinates), length, duration);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "journey.route.unavailable");
    } catch (IOException ex) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "journey.route.unavailable");
    } finally {
      if (process != null) {
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        try {
          if (!process.waitFor(2, TimeUnit.SECONDS))
            process.destroyForcibly().waitFor(2, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
          process.destroyForcibly();
          Thread.currentThread().interrupt();
        }
      }
    }
  }

  static void validate(Point point) {
    if (point == null
        || !Double.isFinite(point.longitude())
        || !Double.isFinite(point.latitude())
        || Math.abs(point.longitude()) >= 180
        || Math.abs(point.latitude()) > 85)
      throw ApiException.badRequest("journey.route.invalid-route");
  }

  static Set<String> tiles(Point a, Point b) {
    Set<String> result = new LinkedHashSet<>();
    int west = (int) Math.floor(Math.min(a.longitude(), b.longitude()) / 5) * 5;
    int east = (int) Math.floor(Math.max(a.longitude(), b.longitude()) / 5) * 5;
    int south = (int) Math.floor(Math.min(a.latitude(), b.latitude()) / 5) * 5;
    int north = (int) Math.floor(Math.max(a.latitude(), b.latitude()) / 5) * 5;
    for (int lon = west; lon <= east; lon += 5)
      for (int lat = south; lat <= north; lat += 5)
        result.add(
            (lon < 0 ? "W" : "E")
                + Math.abs(lon)
                + "_"
                + (lat < 0 ? "S" : "N")
                + Math.abs(lat)
                + ".rd5");
    if (result.size() > 48) throw ApiException.badRequest("journey.route.too-long");
    return result;
  }

  private void ensureTile(String tile, Set<String> required, long downloadDeadline)
      throws IOException, InterruptedException {
    Path directory = cache.resolve("segments4"), target = directory.resolve(tile);
    if (Files.isRegularFile(target)) {
      Files.setLastModifiedTime(target, FileTime.fromMillis(System.currentTimeMillis()));
      return;
    }
    List<Path> stored;
    try (var paths = Files.list(directory)) {
      stored =
          paths
              .filter(path -> path.getFileName().toString().matches("[EW]\\d+_[NS]\\d+\\.rd5"))
              .sorted(Comparator.comparingLong(path -> path.toFile().lastModified()))
              .toList();
    }
    long size = 0;
    for (Path path : stored) size += Files.size(path);
    for (Path path : stored) {
      if (size + MAX_TILE <= MAX_CACHE) break;
      if (!required.contains(path.getFileName().toString())) {
        long removed = Files.size(path);
        Files.delete(path);
        size -= removed;
      }
    }
    if (size + MAX_TILE > MAX_CACHE
        || Files.getFileStore(directory).getUsableSpace() < MAX_TILE * 2)
      throw new IOException("Graph cache full");
    var connection =
        (HttpURLConnection)
            URI.create("https://brouter.de/brouter/segments4/" + tile).toURL().openConnection();
    connection.setConnectTimeout(8000);
    connection.setReadTimeout(15000);
    connection.setInstanceFollowRedirects(false);
    connection.setRequestProperty("User-Agent", "Codoc/1.0 (https://codoc.site)");
    Path temporary = directory.resolve(tile + ".part");
    try {
      if (connection.getResponseCode() != 200 || connection.getContentLengthLong() > MAX_TILE)
        throw new IOException("Graph download unavailable");
      try (InputStream input = connection.getInputStream()) {
        try (OutputStream output = Files.newOutputStream(temporary)) {
          byte[] buffer = new byte[32768];
          long total = 0,
              deadline =
                  Math.min(downloadDeadline, System.nanoTime() + TimeUnit.SECONDS.toNanos(90));
          for (int count; (count = input.read(buffer)) >= 0; ) {
            total += count;
            if (total > MAX_TILE || System.nanoTime() > deadline)
              throw new IOException("Graph download limit");
            output.write(buffer, 0, count);
          }
          if (total < 100) throw new IOException("Invalid graph");
        }
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
      }
    } finally {
      connection.disconnect();
      Files.deleteIfExists(temporary);
    }
  }

  private static double distance(Point a, Point b) {
    double lat = Math.toRadians(b.latitude() - a.latitude()),
        lon = Math.toRadians(b.longitude() - a.longitude());
    double h =
        Math.pow(Math.sin(lat / 2), 2)
            + Math.cos(Math.toRadians(a.latitude()))
                * Math.cos(Math.toRadians(b.latitude()))
                * Math.pow(Math.sin(lon / 2), 2);
    return 12742000 * Math.asin(Math.min(1, Math.sqrt(h)));
  }
}
