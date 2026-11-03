package ru.ticket.generation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.ticket.auth.security.UserIdPrincipal;
import ru.ticket.dto.*;
import ru.ticket.export.ExportFormat;
import ru.ticket.generation.api.GenerationController;
import ru.ticket.generation.application.*;
import ru.ticket.generation.application.dto.*;
import ru.ticket.generation.application.port.*;
import ru.ticket.generation.domain.*;
import ru.ticket.generation.infrastructure.*;
import ru.ticket.schema.domain.FieldDef;
import ru.ticket.template.domain.BindingDto;

class GenerationRegressionTest {
  @Test
  void refusesSchemaDeletionWhenOnlyTemplatesReferenceIt() {
    var schemas = mock(ru.ticket.schema.repository.SchemaRepository.class);
    var models = mock(ru.ticket.datamodel.repository.DataModelRepository.class);
    var templates = mock(ru.ticket.template.repository.TemplateRepository.class);
    when(schemas.findByIdAndUserId("schema", "owner"))
        .thenReturn(
            Optional.of(new ru.ticket.schema.domain.SchemaEntity("owner", "Ticket", List.of())));
    when(templates.countBySchemaId("schema")).thenReturn(1L);
    var service = new ru.ticket.schema.service.SchemaServiceImpl(schemas, models, templates);
    assertThrows(ru.ticket.common.ApiException.class, () -> service.delete("owner", "schema"));
    verify(schemas, never()).delete(any());
  }

  @TempDir Path directory;

  @Test
  void fillsQrChartStampAndEscapedHtmlWithoutMutatingTemplate() {
    var slide = new Slide();
    var qr = new QrElement();
    qr.type = "qr";
    qr.id = "q";
    qr.text = "{{ticket.number}}";
    var text = new TextElement();
    text.type = "text";
    text.id = "t";
    text.text = "{{name}}";
    text.html = "<p>{{name}}</p>";
    var chart = new ChartElement();
    chart.type = "chart";
    chart.id = "c";
    chart.dataPath = "items";
    chart.valueField = "price";
    var stamp = new StampElement();
    stamp.type = "stamp";
    stamp.id = "s";
    var label = new StampTextItem();
    label.text = "{{name}}";
    stamp.texts.add(label);
    slide.elements.addAll(List.of(qr, text, chart, stamp));
    var filled =
        DataFiller.fill(
            slide,
            Map.of(
                "ticket",
                Map.of("number", "123"),
                "name",
                "<Alice>",
                "items",
                List.of(Map.of("price", 5))));
    assertEquals("123", ((QrElement) filled.elements.get(0)).text);
    assertEquals("<p>&lt;Alice&gt;</p>", ((TextElement) filled.elements.get(1)).html);
    assertEquals(List.of(5.0), ((ChartElement) filled.elements.get(2)).values);
    assertEquals("<Alice>", ((StampElement) filled.elements.get(3)).texts.get(0).text);
    assertEquals("{{ticket.number}}", qr.text);
  }

  @Test
  void appliesManualBindingOnlyToItsElement() {
    var slide = new Slide();
    var a = new TextElement();
    a.type = "text";
    a.id = "a";
    a.text = "{{old}}";
    var b = new TextElement();
    b.type = "text";
    b.id = "b";
    b.text = "{{old}}";
    slide.elements.addAll(List.of(a, b));
    var binding = new BindingDto("a", "Text", "{{old}}", "other", "manual", "linked", "old");
    var filled = DataFiller.fill(slide, Map.of("old", "A", "other", "B"), List.of(binding));
    assertEquals("B", ((TextElement) filled.elements.get(0)).text);
    assertEquals("A", ((TextElement) filled.elements.get(1)).text);
  }

  @Test
  void validatesEveryArrayRowAndRejectsUnsafePathsAndTypes() {
    for (String path : List.of("__proto__.x", "a.constructor.x", "a.prototype.x", "a..b"))
      assertThrows(IllegalArgumentException.class, () -> ModelValues.validatePath(path));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ModelValues.validate(
                Map.of("age", "wrong"), List.of(new FieldDef("age", "number", true, null, null))));
    var fields =
        List.of(
            new FieldDef("items", "array", true, null, null),
            new FieldDef("items.name", "string", true, null, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ModelValues.validate(Map.of("items", List.of(Map.of("name", "A"), Map.of())), fields));
    assertDoesNotThrow(
        () -> ModelValues.validate(Map.of("items", List.of(Map.of("name", "A"))), fields));
  }

  @Test
  void preservesNullOptionalFieldsAndBothPathFormats() {
    var input = new LinkedHashMap<String, Object>();
    input.put("optional", null);
    assertTrue(
        ModelValues.validate(input, List.of(new FieldDef("optional", "string", false, null, null)))
            .containsKey("optional"));
    assertEquals(0, ModelValues.get(Map.of("a.b", 0), "a.b"));
    assertEquals(false, ModelValues.get(Map.of("a", List.of(Map.of("b", false))), "a[0].b"));
  }

  @Test
  void usesFilenamePatternAndReportsOnlyInvalidItems() {
    var source = mock(GenerationCatalog.class);
    var renderer = mock(DocumentRenderer.class);
    when(renderer.format()).thenReturn(ExportFormat.SVG);
    when(renderer.renderMulti(anyList(), anyString(), any()))
        .thenAnswer(
            call -> List.of(new NamedDocument(call.getArgument(1) + ".svg", new byte[] {1})));
    when(source.template(eq("u"), any()))
        .thenReturn(
            new GenerationCatalog.Template(
                List.of(new Slide()),
                "s",
                List.of(new FieldDef("name", "string", true, null, null)),
                List.of()));
    when(source.models(anyString(), any())).thenReturn(Map.of());
    var service = new GenerationService(List.of(renderer), List.of(new ZipPackager()), source);
    var good = new GenerationRequest.Item();
    good.inlineValues = Map.of("name", "Alice");
    var bad = new GenerationRequest.Item();
    bad.inlineValues = Map.of();
    var req = new GenerationRequest();
    req.format = "svg";
    req.filenamePattern = "ticket-{{name}}";
    req.items = List.of(good, bad);
    var result = service.generateDocuments("u", req, (i, c, f) -> {});
    assertEquals("ticket-Alice.svg", result.documents().get(0).filename());
    assertEquals(1, result.issues().size());
    assertEquals(1, result.issues().get(0).index());
  }

  @Test
  void completedMetadataAndDownloadsSurviveRestartAndRejectTraversal() {
    var files = new GenerationFiles(directory.toString());
    var data = new DiskJobFileStorage(files);
    var mapper = new ObjectMapper().findAndRegisterModules();
    var jobs = new DiskJobStore(mapper, files);
    var job = new GenerationJob("owner", new GenerationRequest());
    data.save(job.id(), "ticket.svg", new byte[] {1, 2, 3});
    job.start();
    job.done(job.id());
    jobs.create(job);
    var reopened = new DiskJobStore(mapper, new GenerationFiles(directory.toString()));
    assertEquals(GenerationJob.Status.DONE, reopened.find(job.id()).orElseThrow().status());
    assertArrayEquals(new byte[] {1, 2, 3}, new DiskJobFileStorage(files).read(job.id()));
    assertEquals("ticket.svg", data.filename(job.id()));
    assertThrows(IllegalArgumentException.class, () -> data.read("../outside"));
    data.delete(job.id());
    reopened.delete(job.id());
    assertTrue(reopened.findAll().isEmpty());
  }

  @Test
  void interruptedJobsAreTerminalAfterRestart() {
    var files = new GenerationFiles(directory.toString());
    var mapper = new ObjectMapper().findAndRegisterModules();
    var jobs = new DiskJobStore(mapper, files);
    var job = new GenerationJob("u", new GenerationRequest());
    job.start();
    jobs.create(job);
    assertEquals(
        GenerationJob.Status.FAILED,
        new DiskJobStore(mapper, files).find(job.id()).orElseThrow().status());
  }

  @Test
  void pendingStatusAllowsNullResultUrl() {
    var jobs = mock(GenerationJobService.class);
    var job = new GenerationJob("u", new GenerationRequest());
    when(jobs.find("u", job.id())).thenReturn(job);
    var controller =
        new GenerationController(mock(GenerationService.class), jobs, mock(JobFileStorage.class));
    var body = (Map<?, ?>) controller.getJob(new UserIdPrincipal("u"), job.id()).getBody();
    assertNotNull(body);
    assertTrue(body.containsKey("resultUrl"));
    assertNull(body.get("resultUrl"));
  }
}
