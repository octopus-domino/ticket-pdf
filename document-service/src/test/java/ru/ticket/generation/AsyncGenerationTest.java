package ru.ticket.generation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import ru.ticket.generation.application.*;
import ru.ticket.generation.application.dto.*;
import ru.ticket.generation.application.port.JobStore;
import ru.ticket.generation.infrastructure.InMemoryJobStore;

class AsyncGenerationTest {
  @Configuration
  @EnableAsync
  static class Config {
    @Bean(name = "generationExecutor")
    ThreadPoolTaskExecutor executor() {
      var executor = new ThreadPoolTaskExecutor();
      executor.setCorePoolSize(1);
      executor.setWaitForTasksToCompleteOnShutdown(false);
      return executor;
    }

    @Bean
    JobStore<GenerationJob> jobs() {
      return new InMemoryJobStore();
    }

    @Bean
    GenerationService rendering() {
      return mock(GenerationService.class);
    }

    @Bean
    JobFileStorage files() {
      return new InMemoryFileStorage();
    }

    @Bean
    GenerationWorker worker(
        JobStore<GenerationJob> jobs, GenerationService service, JobFileStorage files) {
      return new GenerationWorker(jobs, service, files);
    }

    @Bean
    GenerationJobService service(JobStore<GenerationJob> jobs, GenerationWorker worker) {
      return new GenerationJobService(jobs, worker);
    }
  }

  @Test
  void enqueueReturnsBeforeRenderingAndCancelRemainsTerminal() throws Exception {
    try (var context = new AnnotationConfigApplicationContext(Config.class)) {
      var entered = new CountDownLatch(1);
      var release = new CountDownLatch(1);
      var exited = new CountDownLatch(1);
      when(context
              .getBean(GenerationService.class)
              .generateDocuments(anyString(), any(), any(), any()))
          .thenAnswer(
              call -> {
                entered.countDown();
                release.await();
                try {
                  ((GenerationService.ItemCallback) call.getArgument(2)).onItem(0, 0, 0);
                } finally {
                  exited.countDown();
                }
                return new GenerationService.DocumentsResult(List.of(), List.of());
              });
      var service = context.getBean(GenerationJobService.class);
      var request = new GenerationRequest();
      request.items = List.of(new GenerationRequest.Item());
      var caller = Executors.newSingleThreadExecutor();
      try {
        var queued = caller.submit(() -> service.enqueue("u", request));
        assertTrue(entered.await(10, TimeUnit.SECONDS), "Rendering must start on the worker");
        // A synchronous regression cannot return here: rendering waits for this test to release it.
        GenerationJob job = queued.get(10, TimeUnit.SECONDS);
        assertEquals(1L, release.getCount(), "Enqueue must return while rendering is blocked");
        service.cancel("u", job.id());
        release.countDown();
        assertTrue(exited.await(10, TimeUnit.SECONDS));
        assertEquals(GenerationJob.Status.CANCELLED, job.status());
        assertThrows(RuntimeException.class, () -> service.find("other", job.id()));
      } finally {
        release.countDown();
        caller.shutdownNow();
      }
    }
  }
}
