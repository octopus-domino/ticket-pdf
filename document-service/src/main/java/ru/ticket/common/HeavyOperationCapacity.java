package ru.ticket.common;

import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Component;

/** One shared native/heap-heavy operation on the 2 CPU / 4 GiB host; no waiting queue. */
@Component
public final class HeavyOperationCapacity {
  private final Semaphore permit = new Semaphore(1);

  public boolean tryAcquire() {
    return permit.tryAcquire();
  }

  public void release() {
    permit.release();
  }
}
