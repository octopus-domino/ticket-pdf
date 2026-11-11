package ru.ticket.journey;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.ticket.common.ApiException;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({
  JourneyService.class,
  JourneySiteService.class,
  JourneyContentPolicy.class,
  ObjectMapper.class,
  ru.ticket.workflow.SiteAddressService.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JourneyServiceTest {
  @Test
  void ticketCanBeConsumedOnlyOnceAndCannotBeResetByEditing() throws Exception {
    var input =
        new JourneyService.Input(
            JourneyService.Kind.TICKET,
            "Event",
            Map.of("valid_until", "2100-01-01"),
            List.of("Guest"));
    var issued = service.create(input).get(0);
    String id = issued.document().id();
    String key = issued.managementKey();
    assertThatThrownBy(() -> service.consume(id, "wrong")).isInstanceOf(ApiException.class);
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var start = new java.util.concurrent.CountDownLatch(1);
      java.util.concurrent.Callable<Boolean> attempt =
          () -> {
            start.await();
            try {
              service.consume(id, key);
              return true;
            } catch (ApiException rejected) {
              assertThat(rejected.status()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
              return false;
            }
          };
      var first = pool.submit(attempt);
      var second = pool.submit(attempt);
      start.countDown();
      assertThat(
              List.of(
                  first.get(10, java.util.concurrent.TimeUnit.SECONDS),
                  second.get(10, java.util.concurrent.TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    } finally {
      pool.shutdownNow();
    }
    service.update(id, key, input);
    assertThat(service.status(id).state()).isEqualTo("USED");
    assertThat(service.batch(issued.batchId(), key).documents()).hasSize(1);
    assertThatThrownBy(() -> service.batch(issued.batchId(), "wrong"))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void expiredPassCanBeRenewedAndThenRevoked() {
    var issued =
        service
            .create(
                new JourneyService.Input(
                    JourneyService.Kind.PASS,
                    "Parking",
                    Map.of("valid_until", "2000-01-01"),
                    List.of("Guest")))
            .get(0);
    assertThat(service.status(issued.document().id()).state()).isEqualTo("EXPIRED");
    service.update(
        issued.document().id(),
        issued.managementKey(),
        new JourneyService.Input(
            JourneyService.Kind.PASS, "Parking", Map.of("valid_until", "2100-01-01"), List.of()));
    assertThat(service.status(issued.document().id()).state()).isEqualTo("ACTIVE");
    service.revoke(issued.document().id(), issued.managementKey());
    assertThat(service.status(issued.document().id()).state()).isEqualTo("REVOKED");
  }

  @Test
  void spreadsheetRowsUsePersonalFieldsAndRejectInvalidRowsAtomically() {
    when(codes.next()).thenReturn("zzzzzzzzzzzz", "aaaaaaaaaaaa");
    var result =
        service.create(
            new JourneyService.Input(
                JourneyService.Kind.BADGE,
                "Conference",
                Map.of("issuer", "CoDoc"),
                List.of(),
                List.of(
                    Map.of("recipient", "A", "role", "Speaker"),
                    Map.of("recipient", "B", "role", "Guest"))));
    assertThat(result).hasSize(2);
    assertThat(result.get(0).document().data().get("role")).isEqualTo("Speaker");
    assertThat(result.get(0).managementKey()).isEqualTo(result.get(1).managementKey());
    assertThat(service.batch(result.get(0).batchId(), result.get(0).managementKey()).documents())
        .extracting(value -> value.document().data().get("recipient"))
        .containsExactly("A", "B");
    randomCodes();
    long count = repository.count();
    assertThatThrownBy(
            () ->
                service.create(
                    new JourneyService.Input(
                        JourneyService.Kind.PASS,
                        "Parking",
                        Map.of(),
                        List.of(),
                        List.of(
                            Map.of("recipient", "A", "valid_until", "2100-01-01"),
                            Map.of("recipient", "B", "valid_until", "not-a-date")))))
        .isInstanceOf(ApiException.class);
    assertThat(repository.count()).isEqualTo(count);
  }

  @Autowired JourneyService service;
  @Autowired JourneySiteService sites;
  @Autowired JourneyRepository repository;
  @MockBean ShortCodeGenerator codes;

  @BeforeEach
  void randomCodes() {
    when(codes.next())
        .thenAnswer(
            invocation -> java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12));
  }

  private JourneyService.Input invitation() {
    return new JourneyService.Input(
        JourneyService.Kind.INVITATION, "Свадьба", Map.of("location", "Москва"), List.of());
  }

  @Test
  void publicReadDoesNotGrantManagementAndGuestNamesRemainPrivate() {
    var created = service.create(invitation()).get(0);
    String id = created.document().id();
    assertThat(service.get(id).title()).isEqualTo("Свадьба");
    assertThatThrownBy(() -> service.manage(id, "wrong")).isInstanceOf(ApiException.class);
    service.respond(
        id, new JourneyService.Reply("Анна", true, "visitor-response-key-long-enough-12345"));
    service.respond(
        id, new JourneyService.Reply("Анна", false, "visitor-response-key-long-enough-12345"));
    assertThat(service.guests(id, created.managementKey()))
        .hasSize(1)
        .first()
        .extracting(JourneyService.Guest::attending)
        .isEqualTo(false);
    assertThatThrownBy(() -> service.guests(id, "wrong")).isInstanceOf(ApiException.class);
  }

  @Test
  void collisionRetriesInANewTransactionWithoutOverwritingExistingDocument() {
    when(codes.next()).thenReturn("Abc123Def456");
    var original = service.create(invitation()).get(0);
    when(codes.next()).thenReturn("Abc123Def456", "OtherCode789");
    var second = service.create(invitation()).get(0);
    assertThat(second.document().id()).isEqualTo("OtherCode789");
    assertThat(service.manage(original.document().id(), original.managementKey()).title())
        .isEqualTo("Свадьба");
  }

  @Test
  void menuKeepsItsAddressAndRevocationBlocksAccess() {
    var created =
        service
            .create(
                new JourneyService.Input(
                    JourneyService.Kind.MENU, "Кафе", Map.of("items", "Кофе 200"), List.of()))
            .get(0);
    var updated =
        service.update(
            created.document().id(),
            created.managementKey(),
            new JourneyService.Input(
                JourneyService.Kind.MENU, "Кафе", Map.of("items", "Кофе 250"), List.of()));
    assertThat(updated.id()).isEqualTo(created.document().id());
    assertThat(service.get(updated.id()).data().get("items")).isEqualTo("Кофе 250");
    service.revoke(updated.id(), created.managementKey());
    assertThatThrownBy(() -> service.get(updated.id())).isInstanceOf(ApiException.class);
  }

  @Test
  void failedCertificateBatchLeavesNoPartialPublications() {
    long before = repository.count();
    assertThatThrownBy(
            () ->
                service.create(
                    new JourneyService.Input(
                        JourneyService.Kind.CERTIFICATE,
                        "Курс",
                        Map.of(),
                        List.of("Анна", "X".repeat(101)))))
        .isInstanceOf(ApiException.class);
    assertThat(repository.count()).isEqualTo(before);
  }

  @Test
  void constructorUsesDataDefinitionsAndReservesSystemSubdomains() {
    var definition =
        new JourneySiteService.Definition(
            "my-event",
            JourneyService.Kind.INVITATION,
            "Наш праздник",
            "Приходите",
            "rose",
            List.of(new JourneySiteService.Field("location", "Место", "text", true)));
    var created = sites.create(definition);
    assertThat(sites.get("my-event")).isEqualTo(definition);
    assertThatThrownBy(() -> sites.update("my-event", "wrong", definition))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () ->
                service.create(
                    new JourneyService.Input(
                        JourneyService.Kind.INVITATION,
                        "Встреча",
                        Map.of("site", "my-event"),
                        List.of())))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () ->
                sites.create(
                    new JourneySiteService.Definition(
                        "www",
                        definition.kind(),
                        definition.title(),
                        definition.description(),
                        definition.theme(),
                        definition.fields())))
        .isInstanceOf(ApiException.class);
    assertThat(created.managementKey()).hasSizeGreaterThanOrEqualTo(40);
  }
}
