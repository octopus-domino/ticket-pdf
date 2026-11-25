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

  // Fully configured once; request snapshots must not rediscover modules for every queued job.
  private static final com.fasterxml.jackson.databind.ObjectMapper REQUEST_COPIER =
      com.fasterxml.jackson.databind.json.JsonMapper.builder()
          .addModule(new com.fasterxml.jackson.datatype.jdk8.Jdk8Module())
          .addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
          .build();

  public enum Status {
    QUEUED,
    RUNNING,
    DONE,
    FAILED,
    CANCELLED
  }

  private final String id;
  private final String userId;
  private final GenerationRequest request;
  private final GenerationSource source;
  private final Instant createdAt;
  private final Instant expiresAt;
  private Instant startedAt;
  private Instant finishedAt;
  private volatile Status status = Status.QUEUED;
  private final int total;
  private volatile int completed;
  private volatile int failed;
  private final List<ItemError> errors = new ArrayList<>();
  private volatile String resultPath;

  public GenerationJob(String userId, GenerationRequest request) {
    this(userId, request, null);
  }

  public GenerationJob(String userId, GenerationRequest request, GenerationSource source) {
    this.id = "gen-" + UUID.randomUUID();
    this.userId = userId;
    this.request = REQUEST_COPIER.convertValue(request, GenerationRequest.class);
    this.source = source;
    this.createdAt = Instant.now();
    this.expiresAt = createdAt.plusSeconds(7L * 24 * 60 * 60);
    this.total = request.items != null ? request.items.size() : 0;
  }

  public record Snapshot(
      String id,
      String userId,
      GenerationRequest request,
      Instant createdAt,
      Instant expiresAt,
      Instant startedAt,
      Instant finishedAt,
      Status status,
      int total,
      int completed,
      int failed,
      List<ItemError> errors,
      String resultPath,
      GenerationSource source) {}

  public GenerationJob(Snapshot s) {
    id = s.id();
    userId = s.userId();
    request = s.request();
    source = s.source();
    createdAt = s.createdAt();
    expiresAt = s.expiresAt();
    startedAt = s.startedAt();
    finishedAt = s.finishedAt();
    status = s.status();
    total = s.total();
    completed = s.completed();
    failed = s.failed();
    errors.addAll(s.errors());
    resultPath = s.resultPath();
  }

  public synchronized Snapshot snapshot() {
    return new Snapshot(
        id,
        userId,
        request,
        createdAt,
        expiresAt,
        startedAt,
        finishedAt,
        status,
        total,
        completed,
        failed,
        List.copyOf(errors),
        resultPath,
        source);
  }

  public String id() {
    return id;
  }

  public String userId() {
    return userId;
  }

  public GenerationRequest request() {
    return request;
  }

  public GenerationSource source() {
    return source;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant startedAt() {
    return startedAt;
  }

  public Instant finishedAt() {
    return finishedAt;
  }

  public Instant expiresAt() {
    return expiresAt;
  }

  public Status status() {
    return status;
  }

  public int total() {
    return total;
  }

  public int completed() {
    return completed;
  }

  public int failed() {
    return failed;
  }

  public synchronized List<ItemError> errors() {
    return List.copyOf(errors);
  }

  public String resultPath() {
    return resultPath;
  }

  public synchronized void start() {
    if (terminal()) return;
    status = Status.RUNNING;
    startedAt = Instant.now();
  }

  /** Устанавливает оба счётчика разом — соответствует состоянию "после N обработанных item". */
  public synchronized void progress(int completed, int failed) {
    this.completed = completed;
    this.failed = failed;
  }

  /** Только записывает ошибку, не инкрементит failed — progress уже отвечает за счётчики. */
  public synchronized void recordError(int index, String message) {
    errors.add(new ItemError(index, message));
  }

  public synchronized void fail(String message) {
    if (terminal()) return;
    status = Status.FAILED;
    finishedAt = Instant.now();
    errors.add(new ItemError(-1, message));
  }

  public synchronized void done(String resultPath) {
    if (terminal()) return;
    status = Status.DONE;
    finishedAt = Instant.now();
    this.resultPath = resultPath;
  }

  public synchronized void cancel() {
    if (terminal()) return;
    status = Status.CANCELLED;
    finishedAt = Instant.now();
  }

  private boolean terminal() {
    return status == Status.DONE || status == Status.FAILED || status == Status.CANCELLED;
  }

  public record ItemError(int index, String message) {}
}
