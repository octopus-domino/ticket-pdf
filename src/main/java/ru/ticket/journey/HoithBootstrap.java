package ru.ticket.journey;

import java.nio.file.Path;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.concurrent.*;
import org.slf4j.*;
import org.springframework.beans.factory.DisposableBean;

@Component @ConditionalOnProperty(name="app.hoith.import-on-startup",havingValue="true")
public class HoithBootstrap implements ApplicationRunner,DisposableBean {
  private static final Logger log=LoggerFactory.getLogger(HoithBootstrap.class);
  private final ExecutorService worker=Executors.newSingleThreadExecutor(task->{var thread=new Thread(task,"hoith-import");thread.setDaemon(true);return thread;});
  private final HoithImporter importer;private final BuildingHistoryRepository repository;
  private final String path,cities;private final boolean refresh;
  private final int beforeYear;
  private final boolean background;
  public HoithBootstrap(HoithImporter importer,BuildingHistoryRepository repository,
      @Value("${app.hoith.gpkg-path:./data/hoith.gpkg}") String path,@Value("${app.hoith.cities:}") String cities,
      @Value("${app.hoith.refresh-on-start:false}") boolean refresh,@Value("${app.hoith.before-year:1945}") int beforeYear,
      @Value("${app.hoith.background:false}") boolean background){
    this.importer=importer;this.repository=repository;this.path=path;this.cities=cities;this.refresh=refresh;
    this.beforeYear=beforeYear;
    this.background=background;
  }
  @Override public void run(ApplicationArguments arguments) throws Exception {
    if(!refresh&&repository.countBySource("HOITH")>0)return;
    if(background){
      worker.submit(()->{try{doImport();}catch(Exception failure){log.error("hoith.import failed; completed batches are retained and can be resumed",failure);}});
      log.info("hoith.import scheduled in background before_year={}",beforeYear);
    }else doImport();
  }
  private void doImport() throws Exception {importer.importFile(Path.of(path),new HashSet<>(Arrays.asList(cities.split(","))),beforeYear);}
  @Override public void destroy(){worker.shutdownNow();}
}
