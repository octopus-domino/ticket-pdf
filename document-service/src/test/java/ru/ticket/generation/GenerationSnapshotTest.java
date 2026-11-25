package ru.ticket.generation;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.ticket.generation.application.dto.*;
import ru.ticket.generation.application.port.GenerationCatalog;
import ru.ticket.generation.infrastructure.*;

class GenerationSnapshotTest {
  @TempDir Path directory;
  @Test void completedSourcesSurviveRestartWithoutKeepingPayloadsInTheListCache(){
    var mapper=new ObjectMapper().findAndRegisterModules();var files=new GenerationFiles(directory.toString());
    var request=new GenerationRequest();var item=new GenerationRequest.Item();item.inlineValues=new HashMap<>(Map.of("name","Original"));request.items=new ArrayList<>(List.of(item));
    var source=new GenerationSource(new GenerationCatalog.Template(List.of(),"schema",List.of(),List.of()),Map.of("model",new GenerationCatalog.Model("schema","Original",Map.of("code","001"))));
    var job=new GenerationJob("owner",request,source);item.inlineValues.put("name","Changed");request.items.clear();
    assertEquals("Original",job.request().items.get(0).inlineValues.get("name"));
    job.start();job.progress(1,0);job.done("saved-result.pdf");new DiskJobStore(mapper,files).create(job);
    var restarted=new DiskJobStore(mapper,files);var summary=restarted.findByUser("owner").get(0);
    assertEquals(1,summary.total());assertTrue(summary.request().items.isEmpty());assertNull(summary.source().template());assertTrue(restarted.findByUser("other").isEmpty());
    var restored=restarted.find(job.id()).orElseThrow();assertEquals("001",restored.source().models().get("model").values().get("code"));assertEquals(1,restored.request().items.size());assertEquals(GenerationJob.Status.DONE,restored.status());
  }
  @Test void cancellationUsesTheSameActiveJobObject(){
    var mapper=new ObjectMapper().findAndRegisterModules();var store=new DiskJobStore(mapper,new GenerationFiles(directory.toString()));
    var request=new GenerationRequest();request.items=List.of(new GenerationRequest.Item());var job=store.create(new GenerationJob("owner",request));job.start();store.update(job);
    var cancelling=store.find(job.id()).orElseThrow();assertSame(job,cancelling);cancelling.cancel();store.update(cancelling);
    assertEquals(GenerationJob.Status.CANCELLED,job.status());job.done("late.pdf");assertEquals(GenerationJob.Status.CANCELLED,job.status());
  }
}
