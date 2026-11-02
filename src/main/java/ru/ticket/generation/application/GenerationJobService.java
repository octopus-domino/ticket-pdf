package ru.ticket.generation.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ru.ticket.common.ApiException;
import ru.ticket.generation.application.dto.*;
import ru.ticket.generation.application.port.JobStore;

import java.util.List;

@Service
public class GenerationJobService {

    private static final Logger log = LoggerFactory.getLogger(GenerationJobService.class);

    private final JobStore<GenerationJob> jobStore;
    private final GenerationService generationService;
    private final JobFileStorage fileStorage;

    public GenerationJobService(JobStore<GenerationJob> jobStore,
                                GenerationService generationService,
                                JobFileStorage fileStorage) {
        this.jobStore = jobStore;
        this.generationService = generationService;
        this.fileStorage = fileStorage;
    }

    public GenerationJob enqueue(String userId, GenerationRequest req) {
        GenerationJob job = new GenerationJob(userId, req);
        jobStore.create(job);
        runAsync(job.id());
        return job;
    }

    @Async("generationExecutor")
    public void runAsync(String jobId) {
        GenerationJob job = jobStore.find(jobId).orElseThrow();
        try {
            job.start();
            jobStore.update(job);

            GenerationService.DocumentsResult result = generationService.generateDocuments(
                    job.userId(),
                    job.request(),
                    (idx, completed, failed) -> {
                        job.progress(completed, failed);
                        jobStore.update(job);
                    }
            );

            for (GenerationJob.ItemError err : result.issues()) {
                job.recordError(err.index(), err.message());
            }

            if (result.documents().isEmpty()) {
                job.fail("All items failed");
                jobStore.update(job);
                return;
            }

            PackResult packed = generationService.pack(
                    job.request().packAs, job.request().format, result.documents());
            String path = fileStorage.save(jobId, packed.filename(), packed.content());
            job.done(path);
            jobStore.update(job);

            log.info("generation.job_done job={} completed={} failed={}",
                    jobId, job.completed(), job.failed());
        } catch (Exception e) {
            log.error("generation.job_crashed job={}", jobId, e);
            job.fail(e.getMessage());
            jobStore.update(job);
        }
    }

    public GenerationJob find(String userId, String jobId) {
        GenerationJob j = jobStore.find(jobId)
                .orElseThrow(() -> ApiException.notFound("GenerationJob"));
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