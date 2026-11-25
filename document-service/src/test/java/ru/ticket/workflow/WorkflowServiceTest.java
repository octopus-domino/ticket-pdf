package ru.ticket.workflow;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.ticket.common.ApiException;
import ru.ticket.generation.application.GenerationService;
import ru.ticket.generation.application.dto.*;
import ru.ticket.generation.application.port.GenerationCatalog;

class WorkflowServiceTest {
  final WorkflowRepository repository = mock(WorkflowRepository.class);
  final GenerationService generation = mock(GenerationService.class);
  final ObjectMapper mapper = new ObjectMapper();
  final WorkflowService service =
      new WorkflowService(repository, mapper, generation, mock(SiteAddressService.class), "admin");
  final Map<String, WorkflowEntity> store = new HashMap<>();
  final GenerationSource source =
      new GenerationSource(
          new GenerationCatalog.Template(List.of(), null, List.of(), List.of()), Map.of());

  WorkflowDefinition definition(String title) {
    return new WorkflowDefinition(
        1,
        "my-service",
        title,
        "Description",
        "template",
        List.of(
            new WorkflowDefinition.Step(
                "Details",
                List.of(new WorkflowDefinition.Field("name", "Name", "text", true, List.of())))),
        "Ready");
  }

  @BeforeEach
  void setup() {
    when(repository.findById(anyString()))
        .thenAnswer(a -> Optional.ofNullable(store.get(a.getArgument(0))));
    when(repository.saveAndFlush(any()))
        .thenAnswer(
            a -> {
              WorkflowEntity e = a.getArgument(0);
              store.put(e.slug, e);
              return e;
            });
    when(repository.save(any())).thenAnswer(a -> a.getArgument(0));
    when(generation.freezeSource(eq("owner"), any())).thenReturn(source);
  }

  @Test
  void draftsStayPrivateAndEditsDoNotChangePublishedVersion() {
    var draft = service.save("owner", definition("First"), null);
    assertThatThrownBy(() -> service.publicView("my-service")).isInstanceOf(ApiException.class);
    var published = service.publish("owner", "my-service", draft.revision(), false);
    var publicVersion = service.publicView("my-service");
    service.save("owner", definition("Second"), published.revision());
    assertThat(service.publicView("my-service")).isEqualTo(publicVersion);
    assertThat(publicVersion.definition().title()).isEqualTo("First");
    service.unpublish("owner", "my-service");
    assertThatThrownBy(() -> service.publicView("my-service")).isInstanceOf(ApiException.class);
  }

  @Test
  void rejectsForeignOwnersStaleWritesAndUnauthorizedGlobalPublication() {
    var draft = service.save("owner", definition("First"), null);
    assertThatThrownBy(() -> service.save("stranger", definition("Hijacked"), draft.revision()))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.save("owner", definition("Stale"), 0L))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.publish("owner", "my-service", draft.revision(), true))
        .isInstanceOf(ApiException.class);
    verify(generation, times(1)).freezeSource(anyString(), any());
  }

  @Test
  void executionUsesOnlyPublishedSnapshotAndValidatedInputs() {
    var draft = service.save("owner", definition("First"), null);
    service.publish("owner", "my-service", draft.revision(), false);
    var published = service.publicView("my-service");
    var documents =
        new GenerationService.DocumentsResult(
            List.of(new NamedDocument("document.pdf", new byte[] {1, 2})), List.of());
    when(generation.generateDocuments(eq("owner"), any(), any(), any())).thenReturn(documents);
    when(generation.pack(eq("multipage-pdf"), eq("pdf"), any()))
        .thenReturn(new PackResult(new byte[] {1, 2}, "document.pdf", "application/pdf"));
    assertThat(
            service.generate("my-service", published.revision(), Map.of("name", "Anna")).content())
        .containsExactly(1, 2);
    assertThatThrownBy(
            () ->
                service.generate(
                    "my-service",
                    published.revision(),
                    Map.of("name", "Anna", "templateId", "foreign")))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.generate("my-service", 0, Map.of("name", "Anna")))
        .isInstanceOf(ApiException.class);
    verify(generation)
        .generateDocuments(
            eq("owner"),
            argThat(r -> r.items.get(0).inlineValues.equals(Map.of("name", "Anna"))),
            any(),
            eq(source));
  }

  @Test
  void rejectsUnsafePathsDuplicateFieldsAndInvalidValues() {
    var valid = definition("Form");
    valid.validate();
    assertThatThrownBy(() -> valid.values(Map.of())).isInstanceOf(ApiException.class);
    for (String path : List.of("__proto__.x", "constructor", "name<script>")) {
      var unsafe =
          new WorkflowDefinition(
              1,
              "my-service",
              "Form",
              "",
              "template",
              List.of(
                  new WorkflowDefinition.Step(
                      "Step",
                      List.of(
                          new WorkflowDefinition.Field(path, "Name", "text", true, List.of())))),
              "");
      assertThatThrownBy(unsafe::validate).isInstanceOf(ApiException.class);
    }
  }
}
