package ru.ticket.journey;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class HoithBootstrapTest {
  @Test void aLargeImportDoesNotBlockApplicationStartup() throws Exception {
    var importer=mock(HoithImporter.class);var repository=mock(BuildingHistoryRepository.class);
    var started=new CountDownLatch(1);var release=new CountDownLatch(1);var finished=new CountDownLatch(1);
    when(importer.importFile(any(),anySet(),eq(0))).thenAnswer(call->{started.countDown();release.await();finished.countDown();return 100L;});
    var bootstrap=new HoithBootstrap(importer,repository,"fixture.gpkg","",true,0,true);
    try{
      assertTimeoutPreemptively(java.time.Duration.ofSeconds(2),()->bootstrap.run(new DefaultApplicationArguments()));
      assertTrue(started.await(2,TimeUnit.SECONDS));assertEquals(1,finished.getCount());
      release.countDown();assertTrue(finished.await(2,TimeUnit.SECONDS));
    }finally{release.countDown();bootstrap.destroy();}
  }
}
