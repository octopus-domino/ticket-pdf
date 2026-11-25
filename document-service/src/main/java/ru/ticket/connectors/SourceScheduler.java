package ru.ticket.connectors;


import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Component
public class SourceScheduler {
  private final SourceConnectionRepository sources;
  private final SourceImportService service;

  public SourceScheduler(SourceConnectionRepository sources,SourceImportService service){this.sources=sources;this.service=service;}
  @Scheduled(fixedDelay=60000,initialDelay=120000) public void sync() {
    for(var source:sources.findByAutoSyncMinutesGreaterThan(0)) {
      if(source.lastSyncAt!=null && source.lastSyncAt.plusSeconds(source.autoSyncMinutes*60L).isAfter(Instant.now()))continue;
      sources.markStatus(source.id,Instant.now(),"running");
      try {
        service.sync(source.userId,source.id);
      }catch(RuntimeException error){sources.markStatus(source.id,Instant.now(),"error");}
    }
  }
}
