package ru.ticket.email;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import ru.ticket.email.EmailDtos.EmailItem;
import ru.ticket.email.EmailDtos.EmailOptions;
import ru.ticket.email.render.SlideHtmlRenderer;

class EmailSenderTest {
  @Test
  void buildsMimeWithConfiguredSenderReplyToAndEmbeddedImageWithoutRealDelivery() throws Exception {
    var mail = mock(JavaMailSender.class);
    var message = new MimeMessage(Session.getInstance(new Properties()));
    when(mail.createMimeMessage()).thenReturn(message);
    var sender = new EmailSender(mail, "sender@example.test");
    var item = new EmailItem();
    item.to = "recipient@example.test";
    sender.send(item, new EmailOptions(null, "reply@example.test", "Subject"), "Subject",
        new SlideHtmlRenderer.Result("<p>Hello</p><img src=\"cid:slide\">", Map.of("slide", new byte[] {1, 2, 3}), "Hello"));
    message.saveChanges();
    assertEquals("sender@example.test", message.getFrom()[0].toString());
    assertEquals("reply@example.test", message.getReplyTo()[0].toString());
    assertEquals("Subject", message.getSubject());
    var bytes = new ByteArrayOutputStream();
    message.writeTo(bytes);
    var mime = bytes.toString(StandardCharsets.UTF_8);
    assertTrue(mime.contains("Content-ID: <slide>"));
    assertTrue(mime.contains("multipart/alternative"));
    verify(mail).send(message);
  }
}
