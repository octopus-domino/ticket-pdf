package ru.ticket.journey;

import java.nio.file.Path;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component @ConditionalOnProperty(name="app.hoith.import-on-startup",havingValue="true")
public class HoithBootstrap implements ApplicationRunner {
  private final HoithImporter importer;private final BuildingHistoryRepository repository;
  private final String path,cities;private final boolean refresh;
  private final int beforeYear;
  public HoithBootstrap(HoithImporter importer,BuildingHistoryRepository repository,
      @Value("${app.hoith.gpkg-path:./data/hoith.gpkg}") String path,@Value("${app.hoith.cities:}") String cities,
      @Value("${app.hoith.refresh-on-start:false}") boolean refresh,@Value("${app.hoith.before-year:1945}") int beforeYear){
    this.importer=importer;this.repository=repository;this.path=path;this.cities=cities;this.refresh=refresh;
    this.beforeYear=beforeYear;
  }
  @Override public void run(ApplicationArguments arguments) throws Exception {
    if(!refresh&&repository.countBySource("HOITH")>0)return;
    importer.importFile(Path.of(path),new HashSet<>(Arrays.asList(cities.split(","))),beforeYear);
  }
}
