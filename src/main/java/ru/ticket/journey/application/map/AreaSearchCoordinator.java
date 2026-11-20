package ru.ticket.journey.application.map;

import java.util.concurrent.*;
import java.util.function.Supplier;
import ru.ticket.common.ApiException;
import org.springframework.http.HttpStatus;

/** Single Flight pattern: duplicate callers share work; distinct searches use a bounded queue. */
public final class AreaSearchCoordinator {
  private static final int MAX_PENDING = 32;
  private static final int QUEUE_SECONDS = 22;
  private static final int RESULT_SECONDS = 45;
  private final ConcurrentMap<Object, CompletableFuture<?>> pending = new ConcurrentHashMap<>();
  private final Semaphore providerPermit = new Semaphore(1, true);

  /** Caller cancellation never cancels the shared provider work needed by other callers. */
  @SuppressWarnings("unchecked")
  public <T> T execute(Object key, Supplier<T> action) {
    var result = new CompletableFuture<T>();
    var existing = (CompletableFuture<T>) pending.putIfAbsent(key, result);
    if (existing != null) return await(existing);
    try {
      if (pending.size() > MAX_PENDING) throw busy();
      return executeOwned(action, result);
    } catch (RuntimeException error) {
      result.completeExceptionally(error);
      throw error;
    } finally { pending.remove(key, result); }
  }

  private <T> T executeOwned(Supplier<T> action, CompletableFuture<T> result) {
    boolean acquired = false;
    try {
      acquired = providerPermit.tryAcquire(QUEUE_SECONDS, TimeUnit.SECONDS);
      if (!acquired) throw busy();
      T value = action.get();
      result.complete(value);
      return value;
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw busy();
    } finally { if (acquired) providerPermit.release(); }
  }

  private <T> T await(CompletableFuture<T> result) {
    try { return result.get(RESULT_SECONDS, TimeUnit.SECONDS); }
    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw busy(); }
    catch (TimeoutException timeout) { throw busy(); }
    catch (ExecutionException error) {
      if (error.getCause() instanceof RuntimeException cause) throw cause;
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "journey.route.places-unavailable");
    }
  }

  private ApiException busy() {
    return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "journey.route.search-busy");
  }
}
