package ru.ticket.generation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.ticket.common.ApiException;
import ru.ticket.common.HeavyOperationCapacity;
import ru.ticket.dto.Slide;
import ru.ticket.export.ExportFormat;
import ru.ticket.generation.application.GenerationService;
import ru.ticket.generation.application.dto.*;
import ru.ticket.generation.application.port.*;

class GenerationCapacityTest {
  @Test
  void stopsAnOversizedBatchImmediatelyAndReleasesCapacity() {
    var renderer = mock(DocumentRenderer.class);
    var catalog = mock(GenerationCatalog.class);
    when(renderer.format()).thenReturn(ExportFormat.SVG);
    when(renderer.renderMulti(anyList(), anyString(), any()))
        .thenReturn(List.of(new NamedDocument("large.svg", new byte[32 * 1024 * 1024 + 1])));
    when(catalog.template(anyString(), any()))
        .thenReturn(
            new GenerationCatalog.Template(List.of(new Slide()), null, List.of(), List.of()));
    when(catalog.models(anyString(), any())).thenReturn(Map.of());
    var capacity = new HeavyOperationCapacity();
    var service = new GenerationService(List.of(renderer), List.of(), catalog, capacity);
    var item = new GenerationRequest.Item();
    item.inlineValues = Map.of();
    var request = new GenerationRequest();
    request.format = "svg";
    request.items = List.of(item, item);
    var error =
        assertThrows(
            ApiException.class, () -> service.generateDocuments("owner", request, (i, c, f) -> {}));
    assertTrue(error.getMessage().contains("32 MB"));
    verify(renderer, times(1)).renderMulti(anyList(), anyString(), any());
    assertTrue(capacity.tryAcquire());
    capacity.release();
  }
}
