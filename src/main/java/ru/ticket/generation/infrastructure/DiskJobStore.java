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
      jobs.put(job.id(), compact(job));
      return job;
    } catch (Exception error) {
      throw new IllegalStateException("Cannot persist job", error);
    }
  }

  public synchronized Optional<GenerationJob> find(String id) {
    var cached = jobs.get(id);
    if (cached == null) return Optional.empty();
    if (cached.status() == GenerationJob.Status.QUEUED || cached.status() == GenerationJob.Status.RUNNING)
      return Optional.of(cached);
    try { return Optional.of(new GenerationJob(mapper.readValue(files.path(id, ".json").toFile(), GenerationJob.Snapshot.class))); }
    catch (java.io.IOException error) { throw new IllegalStateException("Cannot load generation job", error); }
  }

  /** Completed snapshots stay on disk; retaining every template/model payload in RAM exhausts small VMs. */
  private GenerationJob compact(GenerationJob job) {
    if (job.status() == GenerationJob.Status.QUEUED || job.status() == GenerationJob.Status.RUNNING) return job;
    var snapshot = job.snapshot();
    var request = new ru.ticket.generation.application.dto.GenerationRequest();
    request.templateId = job.request().templateId; request.schemaId = job.request().schemaId;
    request.format = job.request().format; request.packAs = job.request().packAs; request.items = List.of();
    var marker = job.source() == null ? null : new ru.ticket.generation.application.dto.GenerationSource(null, Map.of());
    return new GenerationJob(new GenerationJob.Snapshot(snapshot.id(),snapshot.userId(),request,snapshot.createdAt(),snapshot.expiresAt(),snapshot.startedAt(),snapshot.finishedAt(),
        snapshot.status(),snapshot.total(),snapshot.completed(),snapshot.failed(),snapshot.errors(),snapshot.resultPath(),marker));
  }

  public synchronized void delete(String id) {
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
