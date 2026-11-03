package ru.ticket.generation.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ru.ticket.generation.application.dto.*;
import ru.ticket.generation.application.port.JobStore;

@Service
public class GenerationWorker {
  private static final Logger log = LoggerFactory.getLogger(GenerationWorker.class);
  private final JobStore<GenerationJob> jobStore;
  private final GenerationService generationService;
  private final JobFileStorage fileStorage;
  private io.micrometer.core.instrument.MeterRegistry metrics =
      new io.micrometer.core.instrument.simple.SimpleMeterRegistry();

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  public void setMetrics(io.micrometer.core.instrument.MeterRegistry metrics) {
    this.metrics = metrics;
  }

  public GenerationWorker(
      JobStore<GenerationJob> jobs, GenerationService service, JobFileStorage files) {
    jobStore = jobs;
    generationService = service;
    fileStorage = files;
  }

  @Async("generationExecutor")
  public void run(String jobId) {
    var timing = io.micrometer.core.instrument.Timer.start(metrics);
    GenerationJob job = jobStore.find(jobId).orElseThrow();
    try {
      if (job.status() == GenerationJob.Status.CANCELLED) return;
      job.start();
      jobStore.update(job);

      GenerationService.DocumentsResult result =
          generationService.generateDocuments(
              job.userId(),
              job.request(),
              (idx, completed, failed) -> {
                if (job.status() == GenerationJob.Status.CANCELLED)
                  throw new java.util.concurrent.CancellationException();
                job.progress(completed, failed);
                jobStore.update(job);
              });

      for (GenerationJob.ItemError err : result.issues()) {
        job.recordError(err.index(), err.message());
      }

      if (result.documents().isEmpty()) {
        job.fail("All items failed");
        jobStore.update(job);
        return;
      }

      PackResult packed =
          generationService.pack(job.request().packAs, job.request().format, result.documents());
      if (job.status() == GenerationJob.Status.CANCELLED) return;
      String path = fileStorage.save(jobId, packed.filename(), packed.content());
      job.done(path);
      jobStore.update(job);

      log.info(
          "generation.job_done job={} completed={} failed={}",
          jobId,
          job.completed(),
          job.failed());
    } catch (java.util.concurrent.CancellationException cancelled) {
      jobStore.update(job);
    } catch (Exception e) {
      log.error("generation.job_crashed job={}", jobId, e);
      job.fail(e.getMessage());
      jobStore.update(job);
    } finally {
      metrics
          .counter("ticket.generation.jobs", "status", job.status().name().toLowerCase())
          .increment();
      timing.stop(metrics.timer("ticket.generation.duration"));
    }
  }
}
