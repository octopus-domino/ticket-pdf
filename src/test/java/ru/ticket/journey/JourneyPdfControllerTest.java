package ru.ticket.journey;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import ru.ticket.dto.ExportRequest;
import ru.ticket.export.*;
import ru.ticket.generation.application.GenerationService;
import ru.ticket.generation.application.dto.PackResult;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JourneyPdfControllerTest {
  @Test void zipOmitsRevokedDocumentsAndKeepsIndividualPublicationNames() {
    var service = mock(JourneyService.class); var factory = mock(JourneyDocumentFactory.class);
    var exporter = mock(ExportFacade.class); var generation = mock(GenerationService.class);
    var active = issued("Active123456", "ACTIVE"); var revoked = issued("Revoke123456", "REVOKED");
    when(service.batch("batch", "key")).thenReturn(new JourneyService.Batch("batch", "Release", List.of(active, revoked)));
    var request = new ExportRequest(); when(factory.document(active.document())).thenReturn(request);
    byte[] pdf = {1, 2, 3}, archive = {4, 5};
    when(exporter.export(ExportFormat.PDF, request)).thenReturn(new ExportResult(pdf, "document.pdf", "application/pdf"));
    when(generation.pack(eq("zip"), eq("pdf"), anyList())).thenAnswer(call -> {
      List<ru.ticket.generation.application.dto.NamedDocument> files = call.getArgument(2);
      assertThat(files).hasSize(1);
      assertThat(files.get(0).filename()).isEqualTo("codoc-Active123456.pdf");
      assertThat(files.get(0).content()).isEqualTo(pdf);
      return new PackResult(archive, "release.zip", "application/zip");
    });
    var controller = new JourneyPdfController(service, factory, exporter, generation);
    assertThat(controller.zip("batch", "key").getBody()).isEqualTo(archive);
    verify(factory, never()).document(revoked.document());
  }

  @Test void failedExportReleasesPreparationCapacityForRetry() {
    var service = mock(JourneyService.class); var factory = mock(JourneyDocumentFactory.class);
    var exporter = mock(ExportFacade.class); var generation = mock(GenerationService.class);
    var active = issued("Active123456", "ACTIVE");
    when(service.batch("batch", "key")).thenReturn(new JourneyService.Batch("batch", "Release", List.of(active)));
    var request = new ExportRequest(); when(factory.document(active.document())).thenReturn(request);
    when(exporter.export(ExportFormat.PDF, request)).thenThrow(new IllegalStateException("Renderer unavailable"))
        .thenReturn(new ExportResult(new byte[]{1}, "document.pdf", "application/pdf"));
    when(generation.pack(eq("zip"), eq("pdf"), anyList())).thenReturn(new PackResult(new byte[]{2}, "release.zip", "application/zip"));
    var controller = new JourneyPdfController(service, factory, exporter, generation);
    assertThatThrownBy(() -> controller.zip("batch", "key")).isInstanceOf(IllegalStateException.class);
    assertThat(controller.zip("batch", "key").getStatusCode().value()).isEqualTo(200);
  }

  private JourneyService.Issued issued(String id, String status) {
    return new JourneyService.Issued(new JourneyService.View(id, JourneyService.Kind.TICKET, "Event", Map.of(), Instant.now()),
        new JourneyContentPolicy.Status(status, null, "", true));
  }
}
