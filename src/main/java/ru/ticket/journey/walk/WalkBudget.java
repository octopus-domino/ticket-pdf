package ru.ticket.journey.walk;

import java.io.IOException;

final class WalkBudget implements AutoCloseable {
  private static final ThreadLocal<Long> DEADLINE=new ThreadLocal<>();
  private final Long previous;
  private WalkBudget(long deadline){previous=DEADLINE.get();DEADLINE.set(previous==null?deadline:Math.min(previous,deadline));}
  static WalkBudget until(long deadline){return new WalkBudget(deadline);}
  static int timeout(int requested) throws IOException {
    Long deadline=DEADLINE.get();if(deadline==null)return requested;
    long remaining=(deadline-System.nanoTime())/1_000_000;
    if(remaining<=0||Thread.currentThread().isInterrupted())throw new IOException("Walk lookup deadline reached");
    return (int)Math.min(requested,remaining);
  }
  public void close(){if(previous==null)DEADLINE.remove();else DEADLINE.set(previous);}
}
