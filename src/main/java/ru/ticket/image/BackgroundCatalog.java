package ru.ticket.image;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.ticket.common.ApiException;

/** Shared static artwork is referenced by id; encoding happens only at export, never in the database. */
@Component
public class BackgroundCatalog {
  private final Path root;
  private final LinkedHashMap<String, String> cache = new LinkedHashMap<>(8, .75f, true);
  private long cachedCharacters;

  public BackgroundCatalog(@Value("${app.backgrounds.path:}") String path) {
    root = (path.isBlank()
        ? Files.isDirectory(Path.of("/usr/share/nginx/html/backgrounds/lossless"))
            ? Path.of("/usr/share/nginx/html/backgrounds/lossless")
            : Path.of("../ticket-editor/public/backgrounds/lossless")
        : Path.of(path)).toAbsolutePath().normalize();
  }

  public synchronized String image(String id) {
    if (id == null || !id.matches("[a-z][a-z0-9-]{0,79}"))
      throw ApiException.badRequest("journey.media.invalid");
    var existing = cache.get(id);
    if (existing != null) return existing;
    Path file = root.resolve(id + ".webp").normalize();
    try {
      if (!file.startsWith(root) || !Files.isRegularFile(file)
          || !file.toRealPath().startsWith(root.toRealPath()) || Files.size(file) > 8_000_000)
        throw ApiException.badRequest("journey.media.invalid");
      String image;
      try (var stream = new MemoryCacheImageInputStream(Files.newInputStream(file))) {
        var readers = ImageIO.getImageReaders(stream);
        if (!readers.hasNext()) throw new IOException("Unsupported background format");
        var reader = readers.next();
        try {
          reader.setInput(stream, true, true);
          int width = reader.getWidth(0), height = reader.getHeight(0);
          if (width < 1 || height < 1 || width > 2400 || height > 2400 || (long) width * height > 4_000_000)
            throw new IOException("Background dimensions exceed export budget");
          var decoded = reader.read(0);
          try { image = "data:image/png;base64," + Base64.getEncoder().encodeToString(LosslessPng.encode(decoded)); }
          finally { decoded.flush(); }
        } finally { reader.dispose(); }
      }
      cache.put(id, image);
      cachedCharacters += image.length();
      while (cache.size() > 4 || cachedCharacters > 12_000_000) {
        var oldest = cache.entrySet().iterator();
        cachedCharacters -= oldest.next().getValue().length();
        oldest.remove();
      }
      return image;
    } catch (IOException unavailable) {
      throw ApiException.badRequest("journey.media.invalid");
    }
  }
}
