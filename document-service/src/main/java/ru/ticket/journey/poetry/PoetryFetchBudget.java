package ru.ticket.journey.poetry;

import java.io.IOException;
import java.time.Duration;

/** One request budget shared by synchronous provider calls; never starts background tasks. */
final class PoetryFetchBudget implements AutoCloseable {
  private static final ThreadLocal<Long> DEADLINE=new ThreadLocal<>();
  private final Long previous;
  private PoetryFetchBudget(Duration duration){previous=DEADLINE.get();long deadline=System.nanoTime()+duration.toNanos();DEADLINE.set(previous==null?deadline:Math.min(previous,deadline));}
  static PoetryFetchBudget open(Duration duration){return new PoetryFetchBudget(duration);}
  static int timeout(int requested) throws IOException {
    var deadline=DEADLINE.get();if(deadline==null)return requested;
    long remaining=(deadline-System.nanoTime())/1_000_000;
    if(remaining<=0)throw new IOException("Poetry request budget exhausted");
    return (int)Math.min(requested,remaining);
  }
  public void close(){if(previous==null)DEADLINE.remove();else DEADLINE.set(previous);}
}
