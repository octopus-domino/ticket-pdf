package ru.ticket.generation.infrastructure;

import java.io.IOException;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** All result and metadata paths are generated IDs, never user filenames. */
@Component
public class GenerationFiles {
  private final Path root;

  public GenerationFiles(
      @Value("${app.generation.storage-dir:./data/generation}") String directory) {
    root = Path.of(directory).toAbsolutePath().normalize();
    try {
      Files.createDirectories(root);
    } catch (IOException error) {
      throw new IllegalStateException(error);
    }
  }

  public Path root() {
    return root;
  }

  public Path path(String id, String suffix) {
    if (!id.matches("gen-[a-fA-F0-9-]{36}")) throw new IllegalArgumentException("Invalid job id");
    return root.resolve(id + suffix);
  }

  public synchronized void write(Path target, byte[] content) {
    try {
      Path temporary = Files.createTempFile(root, "write-", ".tmp");
      try {
        Files.write(temporary, content);
        Files.move(
            temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } finally {
        Files.deleteIfExists(temporary);
      }
    } catch (IOException error) {
      throw new IllegalStateException("Cannot persist generation file", error);
    }
  }
}
