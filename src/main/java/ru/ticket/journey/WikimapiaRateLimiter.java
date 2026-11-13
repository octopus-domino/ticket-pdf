package ru.ticket.journey;

import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Shared by legacy discovery and themed walks. Reserve the last 20% for box/search calls. */
@Component
public class WikimapiaRateLimiter {
  private final Semaphore permit=new Semaphore(1);
  private final AtomicLong minute=new AtomicLong(),daily=new AtomicLong();
  private final int minuteLimit,dailyLimit;
  private final Clock clock;
  private long minuteWindow=-1,dayWindow=-1,nextRequest,warnedMinute=-1;
  @org.springframework.beans.factory.annotation.Autowired
  public WikimapiaRateLimiter(
      @Value("${app.journeys.wikimapia.quota.minute-limit:20}") int minuteLimit,
      @Value("${app.journeys.wikimapia.quota.daily-limit:5000}") int dailyLimit){
    this(minuteLimit,dailyLimit,Clock.systemUTC());
  }
  WikimapiaRateLimiter(int minuteLimit,int dailyLimit,Clock clock){
    this.minuteLimit=Math.max(1,minuteLimit);this.dailyLimit=Math.max(1,dailyLimit);this.clock=clock;
  }
  public Lease acquire(boolean enrichment) throws IOException {
    if(!permit.tryAcquire())throw new IOException("Wikimapia busy");
    synchronized(this){
      long now=clock.millis(),window=now/60000,day=now/86400000;
      if(window!=minuteWindow){minuteWindow=window;minute.set(0);}
      if(day!=dayWindow){dayWindow=day;daily.set(0);}
      long minuteCeiling=enrichment?Math.max(1,minuteLimit*4/5):minuteLimit;
      long dailyCeiling=enrichment?Math.max(1,dailyLimit*49/50):dailyLimit;
      if(minute.get()>=minuteCeiling||daily.get()>=dailyCeiling){
        permit.release();
        if(warnedMinute!=window){warnedMinute=window;LoggerFactory.getLogger(getClass()).warn("wikimapia.quota.warning");}
        throw new IOException("Wikimapia quota unavailable");
      }
      if(now<nextRequest)try{Thread.sleep(Math.min(150,nextRequest-now));now=clock.millis();}
      catch(InterruptedException interrupted){Thread.currentThread().interrupt();permit.release();throw new IOException("Wikimapia interrupted");}
      minute.incrementAndGet();daily.incrementAndGet();nextRequest=now+150;
    }
    return new Lease();
  }
  public boolean reserveDetailsAfterBoxPage(){return minute.get()>=Math.max(1,minuteLimit/4);}
  public final class Lease implements AutoCloseable {
    private boolean closed;
    public void close(){if(!closed){closed=true;permit.release();}}
  }
}
