package ru.ticket.journey.application.map;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AreaSearchCoordinatorTest {
  @Test void identicalCallersShareOneSearchAndDifferentSearchesWaitWithout429() throws Exception {
    var coordinator = new AreaSearchCoordinator();
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var calls = new AtomicInteger();
    var workers = Executors.newFixedThreadPool(3);
    try {
      var first = workers.submit(() -> coordinator.execute("spb-bars", () -> {
        calls.incrementAndGet(); entered.countDown();
        try { assertTrue(release.await(3, TimeUnit.SECONDS)); }
        catch (InterruptedException error) { throw new AssertionError(error); }
        return 41;
      }));
      assertTrue(entered.await(2, TimeUnit.SECONDS));
      var duplicate = workers.submit(() -> coordinator.execute("spb-bars", () -> { fail("Duplicate provider call"); return 0; }));
      var other = workers.submit(() -> coordinator.execute("spb-cultural", () -> 4));
      Thread.sleep(100);
      assertFalse(other.isDone());
      release.countDown();
      assertEquals(41, first.get(3, TimeUnit.SECONDS));
      assertEquals(41, duplicate.get(3, TimeUnit.SECONDS));
      assertEquals(4, other.get(3, TimeUnit.SECONDS));
      assertEquals(1, calls.get());
    } finally { release.countDown(); workers.shutdownNow(); }
  }

  @Test void failureDoesNotPoisonTheNextAttempt() {
    var coordinator = new AreaSearchCoordinator();
    assertThrows(IllegalStateException.class, () -> coordinator.execute("spb", () -> { throw new IllegalStateException(); }));
    assertEquals(4, coordinator.execute("spb", () -> 4));
  }
}
