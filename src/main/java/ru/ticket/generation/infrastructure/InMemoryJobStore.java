package ru.ticket.generation.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import ru.ticket.generation.application.dto.GenerationJob;
import ru.ticket.generation.application.port.JobStore;

public class InMemoryJobStore implements JobStore<GenerationJob> {

  private final Map<String, GenerationJob> jobs = new ConcurrentHashMap<>();

  @Override
  public GenerationJob create(GenerationJob job) {
    jobs.put(job.id(), job);
    return job;
  }

  @Override
  public Optional<GenerationJob> find(String jobId) {
    return Optional.ofNullable(jobs.get(jobId));
  }

  @Override
  public GenerationJob update(GenerationJob job) {
    jobs.put(job.id(), job);
    return job;
  }

  @Override
  public void delete(String jobId) {
    jobs.remove(jobId);
  }

  @Override
  public List<GenerationJob> findByUser(String userId) {
    return jobs.values().stream().filter(j -> j.userId().equals(userId)).toList();
  }

  @Override
  public List<GenerationJob> findAll() {
    return new ArrayList<>(jobs.values());
  }
}
