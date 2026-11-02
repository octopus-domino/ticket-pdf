package ru.ticket.generation.application.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Мутабельный VO задачи. Инкапсулирует переходы состояний и инкрементальный прогресс.
 * Потокобезопасность — за счёт JobStore (он один пишет по jobId).
 */
public final class GenerationJob {

    public enum Status { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

    private final String id;
    private final String userId;
    private final GenerationRequest request;
    private final Instant createdAt;
    private final Instant expiresAt;
    private Instant startedAt;
    private Instant finishedAt;
    private Status status = Status.QUEUED;
    private final int total;
    private int completed;
    private int failed;
    private final List<ItemError> errors = new ArrayList<>();
    private String resultPath;

    public GenerationJob(String userId, GenerationRequest request) {
        this.id = "gen-" + UUID.randomUUID();
        this.userId = userId;
        this.request = request;
        this.createdAt = Instant.now();
        this.expiresAt = createdAt.plusSeconds(7L * 24 * 60 * 60);
        this.total = request.items != null ? request.items.size() : 0;
    }

    public String id() { return id; }
    public String userId() { return userId; }
    public GenerationRequest request() { return request; }
    public Instant createdAt() { return createdAt; }
    public Instant startedAt() { return startedAt; }
    public Instant finishedAt() { return finishedAt; }
    public Instant expiresAt() { return expiresAt; }
    public Status status() { return status; }
    public int total() { return total; }
    public int completed() { return completed; }
    public int failed() { return failed; }
    public List<ItemError> errors() { return List.copyOf(errors); }
    public String resultPath() { return resultPath; }

    public void start() {
        status = Status.RUNNING;
        startedAt = Instant.now();
    }

    /** Устанавливает оба счётчика разом — соответствует состоянию "после N обработанных item". */
    public void progress(int completed, int failed) {
        this.completed = completed;
        this.failed = failed;
    }

    /** Только записывает ошибку, не инкрементит failed — progress уже отвечает за счётчики. */
    public void recordError(int index, String message) {
        errors.add(new ItemError(index, message));
    }

    public void fail(String message) {
        status = Status.FAILED;
        finishedAt = Instant.now();
        errors.add(new ItemError(-1, message));
    }

    public void done(String resultPath) {
        status = Status.DONE;
        finishedAt = Instant.now();
        this.resultPath = resultPath;
    }

    public void cancel() {
        status = Status.CANCELLED;
        finishedAt = Instant.now();
    }

    public record ItemError(int index, String message) { }
}