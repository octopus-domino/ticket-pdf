package ru.ticket.generation.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import ru.ticket.generation.application.dto.GenerationJob;
import ru.ticket.generation.application.port.JobStore;

/**
 * Durable job metadata. Interrupted work is terminal after restart; completed downloads survive.
 */
@Component
public class DiskJobStore implements JobStore<GenerationJob> {
  private final Map<String, GenerationJob> jobs = new ConcurrentHashMap<>();
  private final ObjectMapper mapper;
  private final GenerationFiles files;

  public DiskJobStore(ObjectMapper mapper, GenerationFiles files) {
    this.mapper = mapper;
    this.files = files;
    try (var paths = Files.list(files.root())) {
      for (Path path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
        var job = new GenerationJob(mapper.readValue(path.toFile(), GenerationJob.Snapshot.class));
        if (job.status() == GenerationJob.Status.RUNNING
            || job.status() == GenerationJob.Status.QUEUED)
          job.fail("Server restarted; restart generation");
        update(job);
      }
    } catch (Exception error) {
      throw new IllegalStateException("Cannot load generation jobs", error);
    }
  }

  public GenerationJob create(GenerationJob job) {
    return update(job);
  }

  public synchronized GenerationJob update(GenerationJob job) {
    try {
      files.write(files.path(job.id(), ".json"), mapper.writeValueAsBytes(job.snapshot()));
      jobs.put(job.id(), job);
      return job;
    } catch (Exception error) {
      throw new IllegalStateException("Cannot persist job", error);
    }
  }

  public Optional<GenerationJob> find(String id) {
    return Optional.ofNullable(jobs.get(id));
  }

  public void delete(String id) {
    try {
      Files.deleteIfExists(files.path(id, ".json"));
      jobs.remove(id);
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  public List<GenerationJob> findAll() {
    return List.copyOf(jobs.values());
  }

  public List<GenerationJob> findByUser(String user) {
    return jobs.values().stream().filter(j -> j.userId().equals(user)).toList();
  }
}
