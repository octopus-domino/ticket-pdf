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
      "spring.security.oauth2.client.provider.google.issuer-uri=https://accounts.google.com"
    })
class DocumentFlowTest {
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
