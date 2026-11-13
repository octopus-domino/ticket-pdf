package ru.ticket.journey;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class MysticPlaceBootstrap implements ApplicationRunner {
  private final MysticPlaceRepository repository;
  private final MysticPlaceImporter importer;
  private final ResourceLoader resources;
  private final String path;
  private final boolean refresh;
  public MysticPlaceBootstrap(MysticPlaceRepository repository,MysticPlaceImporter importer,ResourceLoader resources,
      @Value("${app.mystic.csv-path:classpath:mystic_places.csv}") String path,@Value("${app.mystic.refresh-on-start:false}") boolean refresh){
    this.repository=repository;this.importer=importer;this.resources=resources;this.path=path;this.refresh=refresh;
  }
  @Override public void run(ApplicationArguments arguments) throws IOException {
    if(!refresh&&repository.count()!=0)return;
    // ResourceLoader accepts classpath: and file:. Bare paths are explicitly filesystem paths.
    var resource=resources.getResource(path.startsWith("classpath:")||path.startsWith("file:")?path:"file:"+path);
    try(var input=resource.getInputStream()){importer.importFromCsv(input);}
  }
}
