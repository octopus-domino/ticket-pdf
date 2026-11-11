package ru.ticket.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:contract;DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "jwt.secret=contract-test-secret-only-012345678901234567890123456789",
      "app.rate-limit.enabled=false",
      "spring.security.oauth2.client.registration.google.client-id=test",
      "spring.security.oauth2.client.registration.google.client-secret=test",
      "spring.security.oauth2.client.provider.google.issuer-uri=https://accounts.google.com",
      "SMTP_HOST=localhost",
      "SMTP_USER=test@example.invalid",
      "EMAIL_FROM=test@example.invalid",
    })
class DocumentFlowTest {
  @Test
  void newWorkflowsExposeProtectedIssuanceAndPrintableTables() throws Exception {
    var input =
        new ru.ticket.journey.JourneyService.Input(
            ru.ticket.journey.JourneyService.Kind.GIFT,
            "Gift",
            Map.of("amount", "1000", "valid_until", "2100-01-01"),
            List.of("Guest"));
    var issued =
        http.postForEntity(
            "/api/journeys", input, ru.ticket.journey.JourneyService.Created[].class);
    assertEquals(200, issued.getStatusCode().value(), issued.toString());
    var created = issued.getBody()[0];
    String root = "/api/journeys/" + created.document().id();
    assertEquals(
        403, http.postForEntity(root + "/consume", null, String.class).getStatusCode().value());
    var headers = new HttpHeaders();
    headers.set("X-Management-Key", created.managementKey());
    assertEquals(
        200,
        http.exchange(root + "/consume", HttpMethod.POST, new HttpEntity<>(headers), String.class)
            .getStatusCode()
            .value());
    assertEquals(
        409,
        http.exchange(root + "/consume", HttpMethod.POST, new HttpEntity<>(headers), String.class)
            .getStatusCode()
            .value());
    String batch = "/api/journeys/batches/" + created.batchId();
    assertEquals(403, http.getForEntity(batch, String.class).getStatusCode().value());
    assertEquals(
        200,
        http.exchange(batch, HttpMethod.GET, new HttpEntity<>(headers), String.class)
            .getStatusCode()
            .value());
    var badge =
        http.postForEntity(
                "/api/journeys",
                new ru.ticket.journey.JourneyService.Input(
                    ru.ticket.journey.JourneyService.Kind.BADGE,
                    "Conference",
                    Map.of("issuer", "CoDoc"),
                    List.of("A", "B", "C", "D", "E", "F", "G")),
                ru.ticket.journey.JourneyService.Created[].class)
            .getBody()[0];
    headers.set("X-Management-Key", badge.managementKey());
    var pdf =
        http.exchange(
            "/api/journeys/batches/" + badge.batchId() + "/pdf",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            byte[].class);
    assertEquals(200, pdf.getStatusCode().value());
    try (var parsed = org.apache.pdfbox.Loader.loadPDF(pdf.getBody())) {
      assertEquals(2, parsed.getNumberOfPages());
    }
    var proposal =
        http.postForEntity(
                "/api/journeys",
                new ru.ticket.journey.JourneyService.Input(
                    ru.ticket.journey.JourneyService.Kind.PROPOSAL,
                    "Proposal",
                    Map.of(
                        "positions",
                        "["
                            + java.util.stream.IntStream.range(0, 35)
                                .mapToObj(
                                    i ->
                                        "{\"name\":\"Product "
                                            + i
                                            + " "
                                            + "long name ".repeat(10)
                                            + "\",\"quantity\":\"1\",\"price\":\"10.01\"}")
                                .collect(java.util.stream.Collectors.joining(","))
                            + "]"),
                    List.of()),
                ru.ticket.journey.JourneyService.Created[].class)
            .getBody()[0];
    var proposalPdf =
        http.getForEntity("/api/journeys/" + proposal.document().id() + "/pdf", byte[].class);
    assertEquals(200, proposalPdf.getStatusCode().value());
    try (var parsed = org.apache.pdfbox.Loader.loadPDF(proposalPdf.getBody())) {
      var artifactRoot = java.nio.file.Path.of("target", "test-artifacts");
      Files.createDirectories(artifactRoot);
      Files.write(artifactRoot.resolve("proposal.pdf"), proposalPdf.getBody());
      var jsonMapper=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
      Files.write(artifactRoot.resolve("proposal-document.json"),jsonMapper.writeValueAsBytes(proposal.document()));
      var proposalArtwork=http.postForEntity("/api/journeys/artwork",new ru.ticket.journey.JourneyArtworkController.Input(proposal.document().id(),proposal.document().kind(),proposal.document().title(),proposal.document().data()),ru.ticket.journey.JourneyArtworkController.Artwork.class);
      assertEquals(200,proposalArtwork.getStatusCode().value());
      assertEquals(parsed.getNumberOfPages(),proposalArtwork.getBody().pages().size());
      Files.write(artifactRoot.resolve("proposal-artwork.json"),jsonMapper.writeValueAsBytes(proposalArtwork.getBody()));
      javax.imageio.ImageIO.write(
          new org.apache.pdfbox.rendering.PDFRenderer(parsed).renderImageWithDPI(0, 96),
          "png",
          artifactRoot.resolve("proposal-first-page.png").toFile());
      assertTrue(parsed.getNumberOfPages() > 1);
      String text = new org.apache.pdfbox.text.PDFTextStripper().getText(parsed);
      assertTrue(text.contains("350,35"));
      assertTrue(text.contains("Product 34"));
      assertTrue(text.contains("Наименование"));
      assertTrue(text.contains("ИТОГО"));
    }
  }

  @org.springframework.boot.test.context.TestConfiguration
  static class OAuthConfiguration {
    @org.springframework.context.annotation.Bean
    org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
        clientRegistrationRepository() {
      var client =
          org.springframework.security.oauth2.client.registration.ClientRegistration
              .withRegistrationId("google")
              .clientId("test")
              .clientSecret("test")
              .authorizationGrantType(
                  org.springframework.security.oauth2.core.AuthorizationGrantType
                      .AUTHORIZATION_CODE)
              .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
              .authorizationUri("https://example.invalid/authorize")
              .tokenUri("https://example.invalid/token")
              .userInfoUri("https://example.invalid/userinfo")
              .userNameAttributeName("sub")
              .build();
      return new org.springframework.security.oauth2.client.registration
          .InMemoryClientRegistrationRepository(client);
    }
  }

  @Autowired TestRestTemplate http;
  @Autowired ru.ticket.journey.JourneyDocumentFactory journeyDocuments;
  @Autowired ru.ticket.svg.SvgDocumentBuilder journeySvg;

  @Test
  void eventArtworkUsesExactlyThePdfCompositionAndExportsReviewArtifacts() throws Exception {
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
    var dir = java.nio.file.Path.of("target", "test-artifacts", "events");
    Files.createDirectories(dir);
    for (String theme : List.of("rose", "sage", "ink")) {
      var data =
          Map.of(
              "theme",
              theme,
              "recipient",
              "Анна и Михаил",
              "description",
              "Будем рады провести этот вечер вместе с вами.\n"
                  + "Приходите на праздник — будем общаться, танцевать и создавать тёплые"
                  + " воспоминания.",
              "date",
              "2026-12-12",
              "time",
              "18:30",
              "location",
              "Усадьба «Липовый сад»",
              "contact",
              "Пожалуйста, подтвердите участие до 5 декабря.");
      var input =
          new ru.ticket.journey.JourneyService.Input(
              ru.ticket.journey.JourneyService.Kind.INVITATION,
              "Вечер в кругу близких",
              data,
              List.of());
      var created =
          http.postForEntity(
              "/api/journeys", input, ru.ticket.journey.JourneyService.Created[].class);
      assertEquals(200, created.getStatusCode().value());
      var view = created.getBody()[0].document();
      var artwork =
          http.postForEntity(
              "/api/journeys/artwork",
              new ru.ticket.journey.JourneyArtworkController.Input(
                  view.id(), view.kind(), view.title(), view.data()),
              ru.ticket.journey.JourneyArtworkController.Artwork.class);
      assertEquals(200, artwork.getStatusCode().value());
      assertNotNull(artwork.getBody());
      var slides = journeyDocuments.document(view).slides;
      assertEquals(slides.size(), artwork.getBody().pages().size());
      for (int i = 0; i < slides.size(); i++)
        assertEquals(journeySvg.generate(slides.get(i)), artwork.getBody().pages().get(i).svg());
      var pdf = http.getForEntity("/api/journeys/" + view.id() + "/pdf", byte[].class);
      assertEquals(200, pdf.getStatusCode().value());
      Files.write(dir.resolve(theme + ".pdf"), pdf.getBody());
      Files.writeString(dir.resolve(theme + ".json"), mapper.writeValueAsString(artwork.getBody()));
      Files.writeString(dir.resolve(theme + "-document.json"), mapper.writeValueAsString(view));
      try (var parsed = org.apache.pdfbox.Loader.loadPDF(pdf.getBody())) {
        String text = new org.apache.pdfbox.text.PDFTextStripper().getText(parsed);
        assertTrue(text.contains("Анна и Михаил"));
        assertTrue(text.contains("12 декабря 2026"));
        assertFalse(text.contains("2026-12-12"));
        javax.imageio.ImageIO.write(
            new org.apache.pdfbox.rendering.PDFRenderer(parsed).renderImageWithDPI(0, 96),
            "png",
            dir.resolve(theme + "-pdf.png").toFile());
      }
    }
    var font = http.getForEntity("/api/journeys/fonts/serif", byte[].class);
    assertEquals(200, font.getStatusCode().value());
    assertTrue(font.getBody().length > 100000);
    assertEquals(
        404,
        http.getForEntity("/api/journeys/fonts/unknown", String.class).getStatusCode().value());
  }

  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
  @Autowired ru.ticket.generation.infrastructure.GenerationFiles generationFiles;

  @Test
  void publicWorkflowCreatesPrintablePdfProtectsRepliesAndRevokesPublication() {
    var input =
        new ru.ticket.journey.JourneyService.Input(
            ru.ticket.journey.JourneyService.Kind.INVITATION,
            "Birthday",
            Map.of("description", "Welcome friends", "theme", "rose"),
            List.of());
    var created =
        http.postForEntity(
            "/api/journeys", input, ru.ticket.journey.JourneyService.Created[].class);
    assertEquals(200, created.getStatusCode().value(), created.toString());
    var publication = created.getBody()[0];
    String path = "/api/journeys/" + publication.document().id();
    var publicView = http.getForEntity(path, String.class);
    assertEquals(200, publicView.getStatusCode().value());
    assertFalse(publicView.getBody().contains(publication.managementKey()));
    assertEquals(403, http.getForEntity(path + "/responses", String.class).getStatusCode().value());
    var reply =
        new ru.ticket.journey.JourneyService.Reply(
            "Guest", true, "response-key-with-at-least-32-characters");
    assertEquals(
        204, http.postForEntity(path + "/responses", reply, String.class).getStatusCode().value());
    var key = new HttpHeaders();
    key.set("X-Management-Key", publication.managementKey());
    var guests =
        http.exchange(path + "/responses", HttpMethod.GET, new HttpEntity<>(key), String.class);
    assertTrue(guests.getBody().contains("Guest"));
    var pdf = http.getForEntity(path + "/pdf", byte[].class);
    assertEquals(200, pdf.getStatusCode().value(), pdf.toString());
    assertEquals(MediaType.APPLICATION_PDF, pdf.getHeaders().getContentType());
    assertTrue(
        new String(pdf.getBody(), 0, 5, java.nio.charset.StandardCharsets.US_ASCII)
            .startsWith("%PDF-"));
    assertEquals(
        200,
        http.exchange(path, HttpMethod.DELETE, new HttpEntity<>(key), String.class)
            .getStatusCode()
            .value());
    assertEquals(410, http.getForEntity(path, String.class).getStatusCode().value());
  }

  @Test
  void ownProfileCanBeEditedAndOtherAccountsKeepTheirOwnProfile() {
    HttpHeaders owner = registeredHeaders("profile-owner"),
        other = registeredHeaders("profile-other");
    var saved =
        http.exchange(
            "/api/profile",
            HttpMethod.PUT,
            new HttpEntity<>(
                Map.of(
                    "name",
                    "Updated name",
                    "organization",
                    "Studio",
                    "position",
                    "Designer",
                    "phone",
                    "+7 000 000-00-00",
                    "avatar",
                    ""),
                owner),
            Map.class);
    assertEquals(200, saved.getStatusCode().value(), saved.toString());
    assertEquals("Updated name", saved.getBody().get("name"));
    assertEquals("Studio", saved.getBody().get("organization"));
    var isolated =
        http.exchange("/api/profile", HttpMethod.GET, new HttpEntity<>(other), Map.class);
    assertEquals("", isolated.getBody().get("organization"));
    assertNotEquals(saved.getBody().get("id"), isolated.getBody().get("id"));
    assertEquals(
        400,
        http.exchange(
                "/api/profile",
                HttpMethod.PUT,
                new HttpEntity<>(
                    Map.of(
                        "name",
                        "Bad",
                        "organization",
                        "",
                        "position",
                        "",
                        "phone",
                        "",
                        "avatar",
                        "https://external.invalid/avatar.png"),
                    owner),
                String.class)
            .getStatusCode()
            .value());
    assertEquals(401, http.getForEntity("/api/profile", String.class).getStatusCode().value());
  }

  @Test
  void guestCanRenderButCannotPersistAndCreatesNoAccountsRecordsOrJobs() throws Exception {
    var tables = List.of("users", "templates", "schemas", "data_models", "resource_revisions");
    var before =
        tables.stream()
            .map(table -> jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class))
            .toList();
    long filesBefore;
    try (var files = Files.list(generationFiles.root())) {
      filesBefore = files.count();
    }
    var slide =
        Map.of(
            "id",
            "guest-page",
            "name",
            "Guest page",
            "width",
            400,
            "height",
            200,
            "elements",
            List.of(
                Map.of(
                    "id",
                    "label",
                    "type",
                    "text",
                    "x",
                    20,
                    "y",
                    20,
                    "width",
                    300,
                    "height",
                    80,
                    "fontSize",
                    20,
                    "text",
                    "Guest {{name}}")));
    var payload =
        Map.of(
            "inlineSlides",
            List.of(slide),
            "items",
            List.of(Map.of("inlineValues", Map.of("name", "Alice"))),
            "fields",
            List.of(Map.of("path", "name", "type", "string", "required", true)),
            "format",
            "svg",
            "packAs",
            "single");
    var result = http.postForEntity("/api/guest/generate/sync", payload, String.class);
    assertEquals(200, result.getStatusCode().value(), result.toString());
    assertTrue(result.getBody().contains("Alice"));
    assertNull(result.getHeaders().getFirst("X-Generation-History"));
    assertEquals("no-store", result.getHeaders().getFirst("Cache-Control"));
    var pdf =
        http.postForEntity(
            "/api/guest/export/pdf?mode=pdfa",
            Map.of(
                "slides",
                List.of(slide),
                "options",
                Map.of("embedImages", true, "pdfVersion", "pdf/a-2b")),
            byte[].class);
    assertEquals(200, pdf.getStatusCode().value());
    assertTrue(
        new String(pdf.getBody(), 0, 5, java.nio.charset.StandardCharsets.US_ASCII)
            .startsWith("%PDF"));
    assertEquals(
        401,
        http.postForEntity(
                "/api/templates",
                Map.of("name", "Must not save", "slides", List.of(slide), "bindings", List.of()),
                String.class)
            .getStatusCode()
            .value());
    assertEquals(
        before,
        tables.stream()
            .map(table -> jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class))
            .toList());
    try (var files = Files.list(generationFiles.root())) {
      assertEquals(filesBefore, files.count());
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void libraryRolesProtectedFoldersAndOptimisticRevisionAreEnforced() {
    HttpHeaders owner = registeredHeaders("library-owner"),
        editor = registeredHeaders("library-editor"),
        viewer = registeredHeaders("library-viewer"),
        stranger = registeredHeaders("library-stranger");
    var created =
        http.exchange(
            "/api/libraries",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("name", "Brand"), owner),
            Map.class);
    assertEquals(200, created.getStatusCode().value(), created.toString());
    String id = (String) created.getBody().get("id"), path = "/api/libraries/" + id;
    assertEquals(
        404,
        http.exchange(path, HttpMethod.GET, new HttpEntity<>(stranger), String.class)
            .getStatusCode()
            .value());
    assertEquals(
        200,
        http.exchange(
                path + "/members",
                HttpMethod.POST,
                new HttpEntity<>(
                    Map.of("email", "library-editor@example.test", "role", "editor"), owner),
                String.class)
            .getStatusCode()
            .value());
    assertEquals(
        200,
        http.exchange(
                path + "/members",
                HttpMethod.POST,
                new HttpEntity<>(
                    Map.of("email", "library-viewer@example.test", "role", "viewer"), owner),
                String.class)
            .getStatusCode()
            .value());
    var folder = Map.of("id", "folder", "name", "Brand", "kind", "folder", "locked", true);
    var asset =
        Map.of(
            "id",
            "stamp",
            "name",
            "Stamp",
            "kind",
            "stamp",
            "folderId",
            "folder",
            "payload",
            Map.of("text", "Original"));
    var symbol =
        Map.of(
            "id",
            "symbol",
            "name",
            "Logo",
            "kind",
            "symbol",
            "payload",
            Map.of(
                "id",
                "symbol",
                "name",
                "Logo",
                "prototype",
                Map.of("type", "image", "src", "data:image/png;base64,AA==")));
    var items = List.of(folder, asset, symbol);
    assertEquals(
        200,
        http.exchange(
                path,
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("revision", 2, "items", items), owner),
                String.class)
            .getStatusCode()
            .value());
    assertEquals(
        403,
        http.exchange(
                path,
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("revision", 3, "items", items), viewer),
                String.class)
            .getStatusCode()
            .value());
    assertEquals(
        403,
        http.exchange(
                path,
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("revision", 3, "items", List.of(folder)), editor),
                String.class)
            .getStatusCode()
            .value());
    assertEquals(
        409,
        http.exchange(
                path,
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("revision", 2, "items", items), owner),
                String.class)
            .getStatusCode()
            .value());
    assertEquals(
        200,
        http.exchange(
                path,
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("revision", 3, "items", items), editor),
                String.class)
            .getStatusCode()
            .value());
    var listed =
        http.exchange("/api/libraries", HttpMethod.GET, new HttpEntity<>(viewer), String.class);
    assertTrue(listed.getBody().contains(id));
    assertFalse(listed.getBody().contains("Original"));
    http.exchange(
        path + "/members",
        HttpMethod.POST,
        new HttpEntity<>(Map.of("email", "library-editor@example.test", "role", "remove"), owner),
        String.class);
    assertEquals(
        404,
        http.exchange(path, HttpMethod.GET, new HttpEntity<>(editor), String.class)
            .getStatusCode()
            .value());
  }

  private HttpHeaders registeredHeaders(String name) {
    var result =
        http.postForEntity(
            "/api/auth/register",
            Map.of("email", name + "@example.test", "password", "test-password-123", "name", name),
            Map.class);
    assertNotNull(result.getBody(), result.toString());
    var headers = new HttpHeaders();
    headers.setBearerAuth((String) result.getBody().get("accessToken"));
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  @org.springframework.boot.test.mock.mockito.MockBean ru.ticket.clientapi.TykClient tyk;

  @Test
  @SuppressWarnings("unchecked")
  void clientKeyCanReadOnlyItsOwnersDataAndIsRevokedEvenWhenGatewayIsDown() {
    org.mockito.Mockito.when(tyk.enabled()).thenReturn(true);
    var login =
        http.postForEntity(
            "/api/auth/register",
            Map.of(
                "email",
                "client-api-owner@example.test",
                "password",
                "test-password-123",
                "name",
                "Owner"),
            Map.class);
    var ownerHeaders = new HttpHeaders();
    ownerHeaders.setBearerAuth((String) login.getBody().get("accessToken"));
    ownerHeaders.setContentType(MediaType.APPLICATION_JSON);
    var issued =
        http.exchange(
            "/api/client-keys",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("name", "1C", "scopes", java.util.List.of("read"), "days", 30),
                ownerHeaders),
            Map.class);
    assertEquals(200, issued.getStatusCode().value(), issued.toString());
    String token = (String) issued.getBody().get("token");
    String keyId = (String) ((Map<?, ?>) issued.getBody().get("key")).get("id");
    var keyHeaders = new HttpHeaders();
    keyHeaders.set("X-API-Key", token);
    keyHeaders.setContentType(MediaType.APPLICATION_JSON);
    assertEquals(
        200,
        http.exchange("/api/templates", HttpMethod.GET, new HttpEntity<>(keyHeaders), String.class)
            .getStatusCode()
            .value());
    assertEquals(
        403,
        http.exchange(
                "/api/client-keys", HttpMethod.GET, new HttpEntity<>(keyHeaders), String.class)
            .getStatusCode()
            .value());
    assertEquals(
        403,
        http.exchange(
                "/api/templates",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(), keyHeaders),
                String.class)
            .getStatusCode()
            .value());
    var listed =
        http.exchange(
            "/api/client-keys", HttpMethod.GET, new HttpEntity<>(ownerHeaders), String.class);
    assertFalse(listed.getBody().contains(token));
    assertFalse(listed.getBody().contains("encryptedToken"));
    assertFalse(listed.getBody().contains("tokenHash"));
    org.mockito.Mockito.doThrow(new RuntimeException("gateway unavailable"))
        .when(tyk)
        .revoke(token);
    var revoked =
        http.exchange(
            "/api/client-keys/" + keyId,
            HttpMethod.DELETE,
            new HttpEntity<>(ownerHeaders),
            Map.class);
    assertEquals(200, revoked.getStatusCode().value());
    assertEquals(false, revoked.getBody().get("gatewaySynchronized"));
    assertEquals(
        401,
        http.exchange("/api/templates", HttpMethod.GET, new HttpEntity<>(keyHeaders), String.class)
            .getStatusCode()
            .value());
  }

  @Test
  @SuppressWarnings("unchecked")
  void templateLivesInWorkspaceFolderAndGeneratesWithoutSchema() {
    var login =
        http.postForEntity(
            "/api/auth/register",
            Map.of(
                "email",
                "folder-owner@example.test",
                "password",
                "test-password-123",
                "name",
                "Owner"),
            Map.class);
    var headers = new HttpHeaders();
    headers.setBearerAuth((String) login.getBody().get("accessToken"));
    headers.setContentType(MediaType.APPLICATION_JSON);
    var folder =
        http.exchange(
            "/api/workspace/folders",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("name", "Certificates"), headers),
            Map.class);
    assertEquals(201, folder.getStatusCode().value());
    String folderId = (String) folder.getBody().get("id");
    var slide =
        Map.of(
            "id",
            "page",
            "name",
            "Certificate",
            "width",
            200,
            "height",
            100,
            "elements",
            List.of(
                Map.of(
                    "id",
                    "label",
                    "type",
                    "text",
                    "text",
                    "STATIC CERTIFICATE",
                    "x",
                    5,
                    "y",
                    5,
                    "width",
                    190,
                    "height",
                    50,
                    "fontSize",
                    16)));
    var payload =
        Map.of(
            "name",
            "Certificate",
            "folderId",
            folderId,
            "slides",
            List.of(slide),
            "bindings",
            List.of());
    var template =
        http.exchange(
            "/api/templates", HttpMethod.POST, new HttpEntity<>(payload, headers), Map.class);
    assertEquals(201, template.getStatusCode().value(), template.toString());
    String id = (String) template.getBody().get("id");
    assertNull(template.getBody().get("schemaId"));
    assertEquals(folderId, template.getBody().get("folderId"));
    var updated =
        http.exchange(
            "/api/templates/" + id,
            HttpMethod.PUT,
            new HttpEntity<>(
                Map.of("name", "Edited", "slides", List.of(slide), "bindings", List.of()), headers),
            Map.class);
    assertEquals(folderId, updated.getBody().get("folderId"), "Saving content must retain folder");
    var tree =
        http.exchange("/api/workspace/tree", HttpMethod.GET, new HttpEntity<>(headers), List.class);
    Map<String, Object> parent =
        (Map<String, Object>)
            tree.getBody().stream()
                .filter(node -> folderId.equals(((Map<?, ?>) node).get("id")))
                .findFirst()
                .orElseThrow();
    var children = (List<Map<String, Object>>) parent.get("children");
    assertEquals(1, children.size());
    assertEquals(id, children.get(0).get("id"));
    assertEquals("template", children.get(0).get("type"));
    var rendered =
        http.exchange(
            "/api/generate/sync",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of(
                    "templateId",
                    id,
                    "items",
                    List.of(Map.of("inlineValues", Map.of())),
                    "format",
                    "svg",
                    "packAs",
                    "single"),
                headers),
            String.class);
    assertEquals(200, rendered.getStatusCode().value(), rendered.toString());
    assertTrue(rendered.getBody().contains("STATIC CERTIFICATE"));
    String releaseId = rendered.getHeaders().getFirst("X-Generation-History");
    assertNotNull(releaseId);
    assertNotEquals("unavailable", releaseId);
    var savedRelease =
        http.exchange(
            "/api/generate/" + releaseId + "/result",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);
    assertEquals(
        rendered.getBody(), savedRelease.getBody(), "History downloads must preserve exact bytes");
    var versions =
        http.exchange(
            "/api/history/template/" + id, HttpMethod.GET, new HttpEntity<>(headers), List.class);
    assertEquals(2, versions.getBody().size(), "Create and content update are separate versions");
    String revision = (String) ((Map<?, ?>) versions.getBody().get(1)).get("id");
    var restored =
        http.exchange(
            "/api/history/template/" + id + "/" + revision + "/copy",
            HttpMethod.POST,
            new HttpEntity<>(headers),
            Map.class);
    assertEquals(200, restored.getStatusCode().value());
    assertNotEquals(id, restored.getBody().get("id"));
    var outsider =
        http.postForEntity(
            "/api/auth/register",
            Map.of(
                "email",
                "folder-outsider@example.test",
                "password",
                "test-password-123",
                "name",
                "Other"),
            Map.class);
    var otherHeaders = new HttpHeaders();
    otherHeaders.setBearerAuth((String) outsider.getBody().get("accessToken"));
    otherHeaders.setContentType(MediaType.APPLICATION_JSON);
    assertEquals(
        404,
        http.exchange(
                "/api/history/template/" + id + "/" + revision + "/copy",
                HttpMethod.POST,
                new HttpEntity<>(otherHeaders),
                String.class)
            .getStatusCode()
            .value());
    assertEquals(
        403,
        http.exchange(
                "/api/generate/" + releaseId + "/result",
                HttpMethod.GET,
                new HttpEntity<>(otherHeaders),
                String.class)
            .getStatusCode()
            .value());
    var denied =
        http.exchange(
            "/api/templates", HttpMethod.POST, new HttpEntity<>(payload, otherHeaders), Map.class);
    assertEquals(404, denied.getStatusCode().value(), "Cannot create in another user's folder");
    var otherTree =
        http.exchange(
            "/api/workspace/tree", HttpMethod.GET, new HttpEntity<>(otherHeaders), List.class);
    assertTrue(otherTree.getBody().isEmpty());
    var missingFolder =
        http.exchange(
            "/api/templates/" + id + "/parent",
            HttpMethod.PUT,
            new HttpEntity<>(Map.of("parentId", "missing"), headers),
            Map.class);
    assertEquals(404, missingFolder.getStatusCode().value());
    http.exchange(
        "/api/workspace/folders/" + folderId,
        HttpMethod.DELETE,
        new HttpEntity<>(headers),
        Void.class);
    var removed =
        http.exchange("/api/templates/" + id, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    assertEquals(
        404, removed.getStatusCode().value(), "Folder deletion must not leave orphaned templates");
  }

  @DynamicPropertySource
  static void storage(DynamicPropertyRegistry properties) throws Exception {
    String path = Files.createTempDirectory("ticket-contract-").toString();
    properties.add("app.generation.storage-dir", () -> path);
  }

  @SuppressWarnings("unchecked")
  @Test
  void ownerCanCreateModelsTemplatesRenderAndPollJobsButCannotDeleteUsedSchema() throws Exception {
    var login =
        http.postForEntity(
            "/api/auth/register",
            Map.of(
                "email", "contract@example.com", "password", "test-password-123", "name", "Test"),
            Map.class);
    assertTrue(login.getStatusCode().is2xxSuccessful(), login.toString());
    var headers = new HttpHeaders();
    headers.setBearerAuth((String) login.getBody().get("accessToken"));
    headers.setContentType(MediaType.APPLICATION_JSON);
    var fields =
        List.of(
            Map.of("path", "name", "type", "string", "required", true),
            Map.of("path", "optional", "type", "string", "required", false));
    var schema =
        http.exchange(
            "/api/schemas",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("name", "Ticket", "fields", fields), headers),
            Map.class);
    assertTrue(schema.getStatusCode().is2xxSuccessful(), schema.toString());
    String schemaId = (String) schema.getBody().get("id");
    var values = new LinkedHashMap<String, Object>();
    values.put("name", "Alice");
    values.put("optional", null);
    var model =
        http.exchange(
            "/api/data-models",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("schemaId", schemaId, "name", "Alice", "values", values), headers),
            Map.class);
    assertTrue(model.getStatusCode().is2xxSuccessful(), model.toString());
    assertTrue(((Map<?, ?>) model.getBody().get("values")).containsKey("optional"));
    var reimport =
        http.exchange(
            "/api/data-models/import",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of(
                    "schemaId",
                    schemaId,
                    "items",
                    List.of(
                        Map.of(
                            "id", model.getBody().get("id"), "name", "Alice", "values", values))),
                headers),
            Map.class);
    assertEquals(1, reimport.getBody().get("imported"));
    var afterImport =
        http.exchange(
            "/api/data-models?schemaId=" + schemaId,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            List.class);
    assertEquals(1, afterImport.getBody().size(), "Repeated Excel imports update the original ID");
    var foreignImport =
        http.exchange(
            "/api/data-models/import",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of(
                    "schemaId",
                    schemaId,
                    "items",
                    List.of(
                        Map.of("id", "unknown-or-foreign-id", "name", "Alice", "values", values))),
                headers),
            Map.class);
    assertEquals(0, foreignImport.getBody().get("imported"));
    assertEquals(1, ((List<?>) foreignImport.getBody().get("issues")).size());
    var text =
        Map.of(
            "id",
            "text",
            "type",
            "text",
            "text",
            "Ticket {{name}}",
            "x",
            10,
            "y",
            10,
            "width",
            200,
            "height",
            80,
            "fontSize",
            16);
    var qr =
        Map.of(
            "id",
            "qr",
            "type",
            "qr",
            "text",
            "{{name}}",
            "x",
            10,
            "y",
            100,
            "width",
            100,
            "height",
            100);
    var slide =
        Map.of(
            "id",
            "slide",
            "name",
            "Ticket",
            "width",
            400,
            "height",
            300,
            "elements",
            List.of(text, qr));
    var template =
        http.exchange(
            "/api/templates",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of(
                    "schemaId",
                    schemaId,
                    "name",
                    "Ticket",
                    "slides",
                    List.of(slide),
                    "bindings",
                    List.of()),
                headers),
            Map.class);
    assertTrue(template.getStatusCode().is2xxSuccessful(), template.toString());
    String templateId = (String) template.getBody().get("id");
    var workflowDefinition =
        new ru.ticket.workflow.WorkflowDefinition(
            1,
            "contract-workflow",
            "Personal document",
            "Complete your details",
            templateId,
            List.of(
                new ru.ticket.workflow.WorkflowDefinition.Step(
                    "Details",
                    List.of(
                        new ru.ticket.workflow.WorkflowDefinition.Field(
                            "name", "Name", "text", true, List.of())))),
            "Ready to download");
    assertEquals(
        401,
        http.postForEntity(
                "/api/workflows",
                new ru.ticket.workflow.WorkflowController.Save(workflowDefinition, null),
                String.class)
            .getStatusCode()
            .value());
    var workflowDraft =
        http.exchange(
            "/api/workflows",
            HttpMethod.POST,
            new HttpEntity<>(
                new ru.ticket.workflow.WorkflowController.Save(workflowDefinition, null), headers),
            ru.ticket.workflow.WorkflowService.Managed.class);
    assertEquals(200, workflowDraft.getStatusCode().value(), workflowDraft.toString());
    assertEquals(
        404,
        http.getForEntity("/api/public/workflows/contract-workflow", String.class)
            .getStatusCode()
            .value());
    var workflowPublished =
        http.exchange(
            "/api/workflows/contract-workflow/publish",
            HttpMethod.POST,
            new HttpEntity<>(
                new ru.ticket.workflow.WorkflowController.Publish(
                    workflowDraft.getBody().revision(), false),
                headers),
            ru.ticket.workflow.WorkflowService.Managed.class);
    assertEquals(200, workflowPublished.getStatusCode().value(), workflowPublished.toString());
    var publicWorkflow =
        http.getForEntity(
            "/api/public/workflows/contract-workflow",
            ru.ticket.workflow.WorkflowService.PublicView.class);
    assertEquals(200, publicWorkflow.getStatusCode().value());
    var workflowPdf =
        http.postForEntity(
            "/api/public/workflows/contract-workflow/pdf",
            new ru.ticket.workflow.WorkflowController.Run(
                publicWorkflow.getBody().revision(), Map.of("name", "Workflow visitor")),
            byte[].class);
    assertEquals(
        200,
        workflowPdf.getStatusCode().value(),
        new String(workflowPdf.getBody(), java.nio.charset.StandardCharsets.UTF_8));
    try (var generated = org.apache.pdfbox.Loader.loadPDF(workflowPdf.getBody())) {
      assertTrue(
          new org.apache.pdfbox.text.PDFTextStripper()
              .getText(generated)
              .contains("Workflow visitor"));
    }
    var request =
        Map.of(
            "templateId",
            templateId,
            "items",
            List.of(Map.of("dataModelId", model.getBody().get("id"))),
            "format",
            "svg",
            "packAs",
            "single",
            "filenamePattern",
            "ticket-{{name}}");
    var rendered =
        http.exchange(
            "/api/generate/sync",
            HttpMethod.POST,
            new HttpEntity<>(request, headers),
            String.class);
    assertEquals(200, rendered.getStatusCode().value(), rendered.toString());
    assertTrue(rendered.getBody().contains("Alice"));
    assertTrue(rendered.getBody().contains("image/png;base64"));
    assertTrue(
        rendered
            .getHeaders()
            .getFirst(HttpHeaders.CONTENT_DISPOSITION)
            .contains("ticket-Alice.svg"));
    var used =
        http.exchange(
            "/api/schemas/" + schemaId, HttpMethod.DELETE, new HttpEntity<>(headers), Map.class);
    assertEquals(409, used.getStatusCode().value());
    var job =
        http.exchange(
            "/api/generate", HttpMethod.POST, new HttpEntity<>(request, headers), Map.class);
    assertEquals(202, job.getStatusCode().value());
    String jobId = (String) job.getBody().get("jobId");
    Map<String, Object> status = Map.of();
    for (int attempt = 0; attempt < 100; attempt++) {
      var response =
          http.exchange(
              "/api/generate/" + jobId, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
      assertEquals(200, response.getStatusCode().value(), response.toString());
      status = response.getBody();
      if (Set.of("done", "failed").contains(status.get("status"))) break;
      Thread.sleep(50);
    }
    assertEquals("done", status.get("status"), status.toString());
    var result =
        http.exchange(
            "/api/generate/" + jobId + "/result",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);
    assertEquals("image/svg+xml", result.getHeaders().getContentType().toString());
    assertTrue(result.getBody().contains("Alice"));
    assertEquals(
        401,
        http.getForEntity("/api/templates/" + templateId, String.class).getStatusCode().value());
  }
}
