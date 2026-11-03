package ru.ticket.generation.infrastructure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.springframework.stereotype.Component;
import ru.ticket.common.ApiException;
import ru.ticket.generation.application.JobFileStorage;

@Component
public class DiskJobFileStorage implements JobFileStorage {
  private final GenerationFiles files;

  public DiskJobFileStorage(GenerationFiles files) {
    this.files = files;
  }

  public synchronized String save(String id, String filename, byte[] content) {
    if (content.length > 100L * 1024 * 1024) throw ApiException.badRequest("Result exceeds 100 MB");
    try (var paths = Files.list(files.root())) {
      long used =
          paths
              .filter(p -> p.toString().endsWith(".bin"))
              .mapToLong(
                  p -> {
                    try {
                      return Files.size(p);
                    } catch (IOException e) {
                      return 0;
                    }
                  })
              .sum();
      if (used + content.length > 1024L * 1024 * 1024)
        throw ApiException.conflict("Generation storage quota exceeded");
    } catch (IOException error) {
      throw new IllegalStateException(error);
    }
    files.write(files.path(id, ".bin"), content);
    files.write(files.path(id, ".name"), filename.getBytes(StandardCharsets.UTF_8));
    return id;
  }

  public byte[] read(String id) {
    try {
      return Files.readAllBytes(files.path(id, ".bin"));
    } catch (IOException error) {
      throw ApiException.notFound("Job file");
    }
  }

  public String filename(String id) {
    try {
      return Files.readString(files.path(id, ".name"));
    } catch (IOException error) {
      throw ApiException.notFound("Job file");
    }
  }

  public void delete(String id) {
    try {
      Files.deleteIfExists(files.path(id, ".bin"));
      Files.deleteIfExists(files.path(id, ".name"));
    } catch (IOException error) {
      throw new IllegalStateException(error);
    }
  }
}
