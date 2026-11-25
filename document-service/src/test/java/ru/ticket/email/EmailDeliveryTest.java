package ru.ticket.email;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import ru.ticket.datamodel.repository.DataModelRepository;
import ru.ticket.dto.Slide;
import ru.ticket.email.EmailDtos.*;
import ru.ticket.email.render.*;
import ru.ticket.template.repository.TemplateRepository;

class EmailDeliveryTest {
  @Test
  void reportsPartialDeliveryAndNeverResendsConfirmedOrUncertainRecipients() throws Exception {
    var renderer = mock(SlideHtmlRenderer.class);
    when(renderer.render(any(), eq(RenderMode.EMAIL)))
        .thenReturn(new SlideHtmlRenderer.Result("<p>Hello</p>", Map.of(), "Hello"));
    var sender = mock(EmailSender.class);
    var ledger = mock(DeliveryLedger.class);
    var receipts = new HashMap<String, String>();
    when(ledger.reserve(anyString()))
        .thenAnswer(
            call -> {
              String id = call.getArgument(0);
              String previous = receipts.putIfAbsent(id, "PENDING");
              return previous == null ? "RESERVED" : previous;
            });
    doAnswer(
            call -> {
              receipts.put(call.getArgument(0), (boolean) call.getArgument(1) ? "SENT" : "UNKNOWN");
              return null;
            })
        .when(ledger)
        .finish(anyString(), anyBoolean());
    var good = new EmailItem();
    good.to = "good@example.com";
    good.inlineValues = Map.of("name", "Alice");
    var bad = new EmailItem();
    bad.to = "bad@example.com";
    bad.inlineValues = Map.of("name", "Bob");
    doThrow(new IllegalStateException("SMTP interrupted"))
        .when(sender)
        .send(eq(bad), any(), anyString(), any());
    var service =
        new EmailService(
            mock(TemplateRepository.class),
            mock(DataModelRepository.class),
            renderer,
            mock(EmlBuilder.class),
            sender,
            ledger);
    var request = new EmailRequest();
    request.requestId = "retry-safe";
    request.inlineSlides = List.of(new Slide(), new Slide());
    request.items = List.of(good, bad);
    request.inlineSlides.get(0).name = "first";
    request.inlineSlides.get(1).name = "second";
    var first = service.sendAll("owner", request);
    assertEquals(1, first.sent());
    assertEquals(1, first.failed());
    assertEquals("bad@example.com", first.errors().get(0).to());
    var retry = service.sendAll("owner", request);
    assertEquals(1, retry.sent());
    assertEquals(1, retry.failed());
    assertTrue(retry.errors().get(0).message().contains("uncertain"));
    verify(sender, times(1)).send(eq(good), any(), anyString(), any());
    verify(sender, times(1)).send(eq(bad), any(), anyString(), any());
    verify(renderer, times(4))
        .render(argThat(slide -> "first".equals(slide.name)), eq(RenderMode.EMAIL));
  }

  @Test
  void ledgerRetainsTerminalReceiptStatus() {
    var repository = mock(DeliveryReceiptRepository.class);
    var receipt = new DeliveryReceipt("id");
    when(repository.findById("id"))
        .thenReturn(Optional.empty(), Optional.of(receipt), Optional.of(receipt));
    var ledger = new DeliveryLedger(repository);
    assertEquals("RESERVED", ledger.reserve("id"));
    ledger.finish("id", true);
    assertEquals("SENT", ledger.reserve("id"));
    verify(repository, times(1)).saveAndFlush(any());
  }
}
