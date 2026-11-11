package ru.ticket.export;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import ru.ticket.common.ApiException;
import ru.ticket.dto.ExportRequest;

class ExportCapacityTest {
  @Test
  void rejectsConcurrentWorkAndReleasesAfterFailure() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var exporter = mock(DocumentExporter.class);
    when(exporter.format()).thenReturn(ExportFormat.PDF);
    when(exporter.export(any()))
        .thenAnswer(
            call -> {
              entered.countDown();
              if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
              throw new IllegalStateException("Export failed");
            });
    var facade = new ExportFacade(List.of(exporter), new ru.ticket.common.HeavyOperationCapacity());
    var executor = Executors.newSingleThreadExecutor();
    try {
      var running = executor.submit(() -> facade.export(ExportFormat.PDF, new ExportRequest()));
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      assertEquals(
          HttpStatus.TOO_MANY_REQUESTS,
          assertThrows(
                  ApiException.class, () -> facade.export(ExportFormat.PDF, new ExportRequest()))
              .status());
      release.countDown();
      assertThrows(ExecutionException.class, () -> running.get(5, TimeUnit.SECONDS));
      // A failed export must not leave capacity locked.
      assertThrows(
          IllegalStateException.class, () -> facade.export(ExportFormat.PDF, new ExportRequest()));
      verify(exporter, times(2)).export(any());
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }
}
