package ru.ticket.journey;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.ticket.generation.application.GenerationService;
import ru.ticket.generation.application.dto.GenerationRequest;
import ru.ticket.template.service.TemplateService;
import ru.ticket.datamodel.service.DataModelService;
import ru.ticket.datamodel.dto.DataModelDtos;

@SpringBootTest(properties = {
  "spring.datasource.url=jdbc:h2:mem:constructor-copy;DB_CLOSE_DELAY=-1",
  "spring.jpa.hibernate.ddl-auto=create-drop", "app.rate-limit.enabled=false",
  "jwt.secret=constructor-test-secret-only-012345678901234567890123456789",
  "spring.security.oauth2.client.registration.google.client-id=test",
  "spring.security.oauth2.client.registration.google.client-secret=test",
  "SMTP_HOST=localhost", "SMTP_USER=test@example.invalid", "EMAIL_FROM=test@example.invalid"
})
@Transactional
class JourneyConstructorCopyTest {
  @Autowired JourneyService documents;
  @Autowired JourneyConstructorCopyService copies;
  @Autowired TemplateService templates;
  @Autowired DataModelService models;
  @Autowired GenerationService generation;
  @Autowired JourneyPrintSourceService sources;
  @Autowired JourneyDocumentFactory factory;
  @Autowired JourneyRepository repository;
  @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
  @Autowired PublicationsService registry;
  @Autowired JourneyAlbumService albums;
  @Autowired ru.ticket.export.ExportFacade exporter;
  @Autowired JourneyRouteService routes;
  @Autowired JourneyMediaService media;
  @Autowired JourneyPrintSourceRepository printSources;

  @Test void customSiteRebindsDeclaredRootFieldsWithoutReplacingStructuredModelValues() throws Exception {
    var slide = new ru.ticket.dto.Slide(); slide.id = "root-fields";
    var text = new ru.ticket.dto.TextElement(); text.type = "text"; text.id = "guest";
    text.text = "{{guest}}"; text.width = 300; text.height = 60; slide.elements.add(text);
    var fields = List.of(new ru.ticket.schema.domain.FieldDef("guest", "string", false, "Guest", null),
        new ru.ticket.schema.domain.FieldDef("items", "array", false, "Items", null));
    var template = new ru.ticket.generation.application.port.GenerationCatalog.Template(List.of(slide), null, fields, List.of());
    var packet = new JourneyConstructorSource.Packet(template, Map.of("guest", "Old guest", "items", List.of(Map.of("name", "Keep me"))), new ru.ticket.dto.ExportOptions());
    var row = new JourneyPrintSource(); row.id = "site:root-fixture"; row.updatedAt = java.time.Instant.now();
    row.sourceJson = mapper.writeValueAsString(packet); printSources.saveAndFlush(row);
    var definition = new JourneySiteService.Definition("root-fixture", JourneyService.Kind.MENU, "Site", "", "ink", List.of());
    var view = new JourneyService.View("Root12345678", JourneyService.Kind.MENU, "New title",
        Map.of("guest", "Новый гость", "items", "must not replace an array"), java.time.Instant.now());
    var source = factory.constructorSource(view, definition);
    assertThat(source.model().get("guest")).isEqualTo("Новый гость");
    assertThat(source.model().get("items")).isEqualTo(packet.model().get("items"));
    assertThat(((ru.ticket.dto.TextElement) source.materialize().slides.get(0).elements.get(0)).text).isEqualTo("Новый гость");
  }

  @Test void separateWalkMapAndNumberedPhotoCreditsSurviveNativePdf() throws Exception {
    var image = media.save(JourneyMediaTest.image(400, 300, false));
    var stops = List.of(new JourneyRouteService.Stop("home", "Hotel", 13.388, 52.517, "Europe/Berlin", 0),
        new JourneyRouteService.Stop("museum", "Museum", 13.389, 52.518, "Europe/Berlin", 30));
    var legs = List.of(new JourneyRouteService.Leg("foot", 600, null, null, null, null,
        List.of(List.of(13.388, 52.517), List.of(13.389, 52.518)), 150.0));
    var timeline = List.of(new JourneyRouteService.Row("stop", "Museum", "2026-11-12T09:00:00Z", "2026-11-12T09:30:00Z", "Europe/Berlin"));
    var photos = List.of(new JourneyRouteService.Photo("museum", image.id(), "https://commons.wikimedia.org/wiki/File:Fixture.jpg",
        "Fixture photographer", "CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/"));
    String walk = routes.save(new JourneyRouteService.Snapshot(stops, legs, "2026-11-12T09:00", timeline, image.id(), photos)).id();
    var mainStops = List.of(new JourneyRouteService.Stop("home", "Hotel", 13.388, 52.517, "Europe/Berlin", 0, null, walk), stops.get(1));
    String main = routes.save(new JourneyRouteService.Snapshot(mainStops, legs, "2026-11-12T09:00", timeline, image.id())).id();
    var view = new JourneyService.View("Walk12345678", JourneyService.Kind.TOUR, "Trip fixture", Map.of("route", main, "locale", "en"), java.time.Instant.now());
    var request = factory.constructorSource(view).materialize();
    assertThat(request.slides.stream().flatMap(slide -> slide.elements.stream()))
        .filteredOn(element -> element instanceof ru.ticket.dto.ImageElement && element.id.equals("route-map")).hasSize(2);
    var pdf = exporter.export(ru.ticket.export.ExportFormat.PDF, request);
    try (var document = org.apache.pdfbox.Loader.loadPDF(pdf.content())) {
      assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(3);
      String text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
      assertThat(text).contains("City walk", "2. Fixture photographer", "CC BY 4.0", "Fixture.jpg");
    }
  }

  @Test void poetryPrintsSevenNativePagesWithCyrillicChineseAndLatinText() throws Exception {
    var cards = new ArrayList<ru.ticket.journey.poetry.PoetryCardsService.Card>();
    String[] captions = {"Проверка рукописной открытки", "手写排版测试", "Ładne wspomnienia", "Joyeux anniversaire", "Guten Morgen", "Buona giornata", "Buenos días"};
    for (int index = 0; index < captions.length; index++) cards.add(new ru.ticket.journey.poetry.PoetryCardsService.Card(
        "2026-11-0" + (index + 1), "Layout test " + (index + 1), captions[index], "Test author", "Q1", "https://example.test/author", "Q2",
        "https://example.test/source/" + index, "https://example.test/license"));
    var view = new JourneyService.View("Poetry123456", JourneyService.Kind.POETRY, "Layout fixtures", Map.of(), java.time.Instant.now());
    var source = JourneyPoetryTemplate.source(view, new ru.ticket.journey.poetry.PoetryCardsService.Bundle("en", "heartfelt", "middle", cards), "https://codoc.site/d/Poetry123456");
    var pdf = exporter.export(ru.ticket.export.ExportFormat.PDF, source.materialize());
    try (var document = org.apache.pdfbox.Loader.loadPDF(pdf.content())) {
      assertThat(document.getNumberOfPages()).isEqualTo(7);
      String text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
      assertThat(text).contains(captions);
    }
  }

  @Test void realConstructorResourcesRemainEditableAndUseTheNormalGenerationEngine() {
    var issued = documents.create(new JourneyService.Input(JourneyService.Kind.INVITATION,
        "Our celebration", Map.of("date","2100-01-01","location","Garden","theme","rose"), List.of("Мария")), "constructor-owner").get(0);
    var copy = copies.copy("constructor-owner", issued.document().id(), null);
    var template = templates.get("constructor-owner", copy.templateId());
    var model = models.get("constructor-owner", copy.modelId());
    assertThat(template.schemaId()).isEqualTo(copy.schemaId());
    assertThat(model.schemaId()).isEqualTo(copy.schemaId());
    assertThat(template.bindings()).isNotEmpty();
    var changed = new LinkedHashMap<String,Object>(model.values());
    var fields = new LinkedHashMap<>((Map<String,Object>)changed.get("fields"));
    fields.put("recipient", "Анна & Лев"); changed.put("fields", fields);
    models.update("constructor-owner", copy.modelId(), new DataModelDtos.UpdateModelRequest("New guest",changed));
    var request = new GenerationRequest(); request.templateId=copy.templateId(); request.format="svg";
    var item = new GenerationRequest.Item(); item.dataModelId=copy.modelId(); request.items=List.of(item);
    var rendered = generation.generateDocuments("constructor-owner",request,(index,completed,failed)->{});
    assertThat(rendered.issues()).isEmpty();
    assertThat(rendered.documents()).isNotEmpty();
    assertThat(new String(rendered.documents().get(0).content(),java.nio.charset.StandardCharsets.UTF_8)).contains("Анна");
    assertThatThrownBy(()->templates.get("another-owner",copy.templateId())).isInstanceOf(ru.ticket.common.ApiException.class);
  }

  @Test void anotherAccountCannotCopyAPrivatePublicationWithoutItsManagementKey() {
    var issued=documents.create(new JourneyService.Input(JourneyService.Kind.MENU,"Menu",Map.of("items","Tea"),List.of()),"constructor-owner").get(0);
    assertThatThrownBy(()->copies.copy("another-owner",issued.document().id(),null)).isInstanceOf(ru.ticket.common.ApiException.class);
    assertThat(templates.list("another-owner",null)).isEmpty();
    var delegated=copies.copy("another-owner",issued.document().id(),issued.managementKey());
    assertThat(templates.get("another-owner",delegated.templateId())).isNotNull();
  }

  @Test void savedNativeSourceCanPublishANewRecordWithoutChangingTheOriginalOrReusingItsQr() {
    var original = documents.create(new JourneyService.Input(JourneyService.Kind.INVITATION, "Celebration",
        Map.of("date", "2100-01-01", "location", "Garden", "recipient", "First guest"), List.of()), "constructor-owner").get(0);
    var copy = copies.copy("constructor-owner", original.document().id(), null);
    var model = models.get("constructor-owner", copy.modelId());
    var changed = new LinkedHashMap<String, Object>(model.values());
    var fields = new LinkedHashMap<>((Map<String, Object>) changed.get("fields"));
    fields.put("recipient", "New guest"); changed.put("fields", fields);
    models.update("constructor-owner", copy.modelId(), new DataModelDtos.UpdateModelRequest("New guest", changed));
    var input = new JourneyPrintSourceService.Input(copy.templateId(), copy.modelId());
    var created = sources.createPublication("constructor-owner", original.document().id(), null, input);
    assertThat(created.document().id()).isNotEqualTo(original.document().id());
    assertThat(created.document().data().get("recipient")).isEqualTo("New guest");
    assertThat(documents.get(original.document().id()).data().get("recipient")).isEqualTo("First guest");
    var printed = factory.document(created.document());
    assertThat(printed.slides.stream().flatMap(slide -> slide.elements.stream()))
        .anyMatch(element -> element instanceof ru.ticket.dto.TextElement text && text.text.contains("New guest"));
    assertThat(printed.slides.stream().flatMap(slide -> slide.elements.stream()).filter(element -> element instanceof ru.ticket.dto.QrElement)
        .map(element -> ((ru.ticket.dto.QrElement) element).text)).allMatch(url -> url.contains(created.document().id()));
    assertThatThrownBy(() -> sources.publication("other-owner", original.document().id(), null, input)).isInstanceOf(ru.ticket.common.ApiException.class);
    sources.publication("constructor-owner", original.document().id(), null, input);
    // Publishing freezes a source: later model edits cannot silently rewrite the existing PDF.
    fields.put("recipient", "Later draft");
    models.update("constructor-owner", copy.modelId(), new DataModelDtos.UpdateModelRequest("Later draft", changed));
    assertThat(factory.document(original.document()).slides.stream().flatMap(slide -> slide.elements.stream()))
        .anyMatch(element -> element instanceof ru.ticket.dto.TextElement text && text.text.contains("New guest"));
  }

  @Test void allReadyKindsCreateRealReusableConstructorResources() throws Exception {
    for (var kind : JourneyService.Kind.values()) {
      var entity = new JourneyEntity(); entity.id = String.format("Doc%09d", kind.ordinal());
      entity.kind = kind.name(); entity.title = "Example"; entity.ownerId = "constructor-owner";
      entity.managementHash = "a".repeat(64); entity.createdAt = java.time.Instant.now(); entity.updatedAt = entity.createdAt;
      entity.dataJson = mapper.writeValueAsString(Map.of("recipient", "Guest", "issuer", "Studio", "description", "Welcome",
          "date", "2100-01-01", "location", "Garden", "theme", "sage", "events",
          "[{\"id\":\"lesson-1\",\"title\":\"Lesson\",\"date\":\"2100-01-01\",\"time\":\"12:00\",\"location\":\"Online\",\"durationMinutes\":60}]",
          "positions", "[{\"name\":\"Service\",\"quantity\":\"2\",\"price\":\"1200\"}]"));
      repository.saveAndFlush(entity);
      var copy = copies.copy("constructor-owner", entity.id, null);
      var template = templates.get("constructor-owner", copy.templateId());
      assertThat(template.slides()).as(kind.name()).isNotEmpty();
      assertThat(template.bindings()).as(kind.name()).isNotEmpty();
      var request = new GenerationRequest(); request.templateId = copy.templateId(); request.format = "svg";
      var item = new GenerationRequest.Item(); item.dataModelId = copy.modelId(); request.items = List.of(item);
      var output = generation.generateDocuments("constructor-owner", request, (i, completed, failed) -> {});
      assertThat(output.issues()).as(kind.name()).isEmpty();
      assertThat(output.documents()).as(kind.name()).isNotEmpty();
    }
  }

  @Test void releasesRemainGroupedAndStatusCommandsKeepManagementAccessibleAfterRevocation() {
    var issued = documents.create(new JourneyService.Input(JourneyService.Kind.TICKET, "Tickets",
        Map.of("date", "2100-01-01", "valid_until", "2100-01-01", "location", "Garden"), List.of("Guest A", "Guest B")), "constructor-owner");
    var first = issued.get(0); var id = first.document().id(); var key = first.managementKey();
    assertThat(registry.batches("constructor-owner", 0).items()).anyMatch(batch -> batch.id().equals(first.batchId()) && batch.count() == 2);
    assertThat(registry.batches("another-owner", 0).items()).isEmpty();
    assertThat(documents.batchOwned(first.batchId(), "constructor-owner").documents()).hasSize(2);
    assertThatThrownBy(() -> documents.batchOwned(first.batchId(), "another-owner")).isInstanceOf(ru.ticket.common.ApiException.class);
    assertThat(documents.consume(id, key).state()).isEqualTo("USED");
    assertThat(documents.changeStatus(id, key, null, new JourneyService.StatusChange("REVOKE")).state()).isEqualTo("REVOKED");
    assertThatThrownBy(() -> documents.get(id)).isInstanceOf(ru.ticket.common.ApiException.class);
    assertThat(documents.manage(id, key).id()).isEqualTo(id);
    assertThat(documents.changeStatus(id, null, "constructor-owner", new JourneyService.StatusChange("RESTORE")).state()).isEqualTo("USED");
    assertThat(documents.changeStatus(id, key, null, new JourneyService.StatusChange("RESET_USAGE")).state()).isEqualTo("ACTIVE");
    assertThat(documents.status(issued.get(1).document().id()).state()).isEqualTo("ACTIVE");
    assertThatThrownBy(() -> documents.changeStatus(id, null, "another-owner", new JourneyService.StatusChange("REVOKE"))).isInstanceOf(ru.ticket.common.ApiException.class);
  }

  @Test void oneSavedDesignUpdatesTheReleaseWithoutReplacingRecipientsQrOrAdmissionMarks() {
    var issued = documents.create(new JourneyService.Input(JourneyService.Kind.TICKET, "Tickets",
        Map.of("date", "2100-01-01", "valid_until", "2100-01-01", "location", "Garden"), List.of("Guest A", "Guest B")), "constructor-owner");
    var first = issued.get(0);
    var copy = copies.copy("constructor-owner", first.document().id(), null);
    var template = templates.get("constructor-owner", copy.templateId());
    template.slides().get(0).background = "#123456";
    templates.update("constructor-owner", copy.templateId(), new ru.ticket.template.dto.TemplateDtos.SaveTemplateRequest(template.schemaId(), template.name(), template.slides(), template.bindings(), template.folderId()));
    documents.consume(first.document().id(), first.managementKey());
    sources.batch("constructor-owner", first.batchId(), null, new JourneyPrintSourceService.Input(copy.templateId(), copy.modelId()));
    for (var value : issued) {
      var printed = factory.document(value.document());
      assertThat(printed.slides.get(0).background).isEqualTo("#123456");
      assertThat(printed.slides.stream().flatMap(page -> page.elements.stream()))
          .anyMatch(element -> element instanceof ru.ticket.dto.TextElement text && text.text.contains(value.document().data().get("recipient")));
      assertThat(printed.slides.stream().flatMap(page -> page.elements.stream()).filter(element -> element instanceof ru.ticket.dto.QrElement)
          .map(element -> ((ru.ticket.dto.QrElement) element).text)).allMatch(url -> url.contains(value.document().id()));
    }
    assertThat(documents.status(first.document().id()).state()).isEqualTo("USED");
    assertThat(documents.status(issued.get(1).document().id()).state()).isEqualTo("ACTIVE");
  }

  @Test void collaborativeAlbumProtectsContributionsAndUsesRepeatedNativePagesWithRealHandwriting() throws Exception {
    var created = documents.create(new JourneyService.Input(JourneyService.Kind.ALBUM, "Наш день рождения",
        Map.of("description", "Воспоминания друзей", "locale", "ru"), List.of()), "constructor-owner").get(0);
    String id = created.document().id(), key = created.managementKey();
    String invitation = albums.invitation(id, null, key, false);
    assertThat(albums.invitation(id, null, key, false)).isEqualTo(invitation);
    String image = JourneyMediaTest.image(100, 80, false);
    var first = albums.add(id, invitation, new JourneyAlbumService.Input(image, "Первая фотография", "Анна"));
    var second = albums.add(id, invitation, new JourneyAlbumService.Input(image, "Second photo", "Alex"));
    assertThat(albums.get(id, null, null).photos()).hasSize(2);
    assertThatThrownBy(() -> albums.reorder(id, null, key,
        new JourneyAlbumService.Order(first.album().revision(), List.of(second.photoId(), first.photoId())))).isInstanceOf(ru.ticket.common.ApiException.class);
    var ordered = albums.reorder(id, null, key, new JourneyAlbumService.Order(second.album().revision(), List.of(second.photoId(), first.photoId())));
    assertThat(ordered.photos().get(0).id()).isEqualTo(second.photoId());
    assertThatThrownBy(() -> albums.edit(id, first.photoId(), null, null, "wrong-edit-key", new JourneyAlbumService.Edit("Bad", ""), false))
        .isInstanceOf(ru.ticket.common.ApiException.class);
    albums.edit(id, first.photoId(), null, null, first.editKey(), new JourneyAlbumService.Edit("С любовью", "Анна"), false);
    String replacement = albums.invitation(id, null, key, true);
    assertThatThrownBy(() -> albums.add(id, invitation, new JourneyAlbumService.Input(image, "No access", "")))
        .isInstanceOf(ru.ticket.common.ApiException.class);
    albums.add(id, replacement, new JourneyAlbumService.Input(image, "生日快乐", "朋友"));
    albums.add(id, replacement, new JourneyAlbumService.Input(image, "Joyeux anniversaire", "Amélie"));
    albums.add(id, replacement, new JourneyAlbumService.Input(image, "Ładne wspomnienia", "Łukasz"));
    var source = factory.constructorSource(created.document());
    assertThat(source.template().slides().get(0).repeat.pageSize()).isEqualTo(4);
    assertThat(source.materialize().slides).hasSize(2);
    var pdf = exporter.export(ru.ticket.export.ExportFormat.PDF, source.materialize());
    try (var document = org.apache.pdfbox.Loader.loadPDF(pdf.content())) {
      assertThat(document.getNumberOfPages()).isEqualTo(2);
      String text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
      assertThat(text).contains("С любовью", "生日快乐", "Ładne wspomnienia");
    }
    String publicJson = mapper.writeValueAsString(albums.get(id, null, null));
    assertThat(publicJson).doesNotContain("imageId", "editHash", "invitation", "editKey");
    var copy = copies.copy("constructor-owner", id, null);
    sources.publication("constructor-owner", id, null, new JourneyPrintSourceService.Input(copy.templateId(), copy.modelId()));
    albums.add(id, replacement, new JourneyAlbumService.Input(image, "Новая фотография", "Друг"));
    assertThat((List<?>)factory.constructorSource(created.document()).model().get("photos")).hasSize(6);
    var independent = sources.createPublication("constructor-owner", id, null, new JourneyPrintSourceService.Input(copy.templateId(), copy.modelId()));
    assertThat(albums.get(independent.document().id(), "constructor-owner", null).photos()).hasSize(5);
    assertThat(albums.get(id, null, null).photos()).hasSize(6);
  }
}
