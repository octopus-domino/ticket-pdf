package ru.ticket.generation.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.ticket.generation.application.dto.GenerationJob;
import ru.ticket.generation.application.port.JobStore;

import java.time.Instant;

/**
 * Чистит просроченные job'ы и их файлы.
 * NFR-OBS: TTL 7 дней, cron — раз в час.
 */
@Service
public class GenerationCleanupService {

    private static final Logger log = LoggerFactory.getLogger(GenerationCleanupService.class);

    private final JobStore<GenerationJob> jobs;
    private final JobFileStorage files;

    public GenerationCleanupService(JobStore<GenerationJob> jobs, JobFileStorage files) {
        this.jobs = jobs;
        this.files = files;
    }

    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 600_000L)
    public void cleanup() {
        Instant now = Instant.now();
        int removed = 0;
        for (GenerationJob job : jobs.findAll()) {
            if (job.expiresAt().isBefore(now)) {
                try { files.delete(job.id()); } catch (Exception ignored) { /* file may not exist */ }
                jobs.delete(job.id());
                removed++;
            }
        }
        if (removed > 0) {
            log.info("generation.cleanup removed={}", removed);
        }
    }
}