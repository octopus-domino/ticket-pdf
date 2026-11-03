package ru.ticket.generation.application;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.ticket.common.ApiException;
import ru.ticket.generation.application.dto.*;
import ru.ticket.generation.application.port.JobStore;

@Service
public class GenerationJobService {

  private static final Logger log = LoggerFactory.getLogger(GenerationJobService.class);

  private final JobStore<GenerationJob> jobStore;
  private final GenerationWorker worker;

  public GenerationJobService(JobStore<GenerationJob> jobStore, GenerationWorker worker) {
    this.jobStore = jobStore;
    this.worker = worker;
  }

  public synchronized GenerationJob enqueue(String userId, GenerationRequest req) {
    if (jobStore.findByUser(userId).stream()
            .filter(
                j ->
                    j.status() == GenerationJob.Status.QUEUED
                        || j.status() == GenerationJob.Status.RUNNING)
            .count()
        >= 2) throw ApiException.conflict("Two generation jobs are already active");
    GenerationJob job = new GenerationJob(userId, req);
    jobStore.create(job);
    try {
      worker.run(job.id());
    } catch (RuntimeException error) {
      job.fail("Generation queue is full");
      jobStore.update(job);
      throw error;
    }
    return job;
  }

  public GenerationJob find(String userId, String jobId) {
    GenerationJob j =
        jobStore.find(jobId).orElseThrow(() -> ApiException.notFound("GenerationJob"));
    if (!j.userId().equals(userId)) throw ApiException.forbidden("Access denied");
    return j;
  }

  public List<GenerationJob> listByUser(String userId) {
    return jobStore.findByUser(userId);
  }

  public void cancel(String userId, String jobId) {
    GenerationJob j = find(userId, jobId);
    j.cancel();
    jobStore.update(j);
  }
}
