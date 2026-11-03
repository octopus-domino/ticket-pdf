package ru.ticket.svg;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.util.*;
import org.junit.jupiter.api.Test;
import ru.ticket.email.*;
import ru.ticket.email.render.SlideHtmlRenderer;
import ru.ticket.image.PublicImageDownloader;

class RenderingSecurityTest {
  @Test
  void stripsActiveTagsHandlersRemoteUsesAndUnsafeStyles() {
    String clean =
        SvgSanitizer.sanitize(
            "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script><g onclick='bad()'"
                + " style='fill:red;background:url(http://internal)'><use"
                + " href='http://internal/x'/><rect width='10' height='10'/></g></svg>");
    assertFalse(clean.contains("script"));
    assertFalse(clean.contains("onclick"));
    assertFalse(clean.contains("internal"));
    assertTrue(clean.contains("fill:red"));
  }

  @Test
  void rejectsDtdAndPrivateNetworkAddresses() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SvgSanitizer.sanitize(
                "<!DOCTYPE svg [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><svg>&x;</svg>"));
    for (String address :
        new String[] {"127.0.0.1", "10.0.0.1", "169.254.169.254", "::1", "fc00::1", "100.64.0.1"})
      assertFalse(PublicImageDownloader.isPublic(InetAddress.getByName(address)));
    assertTrue(PublicImageDownloader.isPublic(InetAddress.getByName("8.8.8.8")));
  }

  @Test
  void emlPreservesRecipientsAndAlternativeBodies() throws Exception {
    var item = new EmailDtos.EmailItem();
    item.to = "to@example.com";
    item.cc = List.of("cc@example.com");
    item.bcc = List.of("bcc@example.com");
    var bytes =
        new EmlBuilder()
            .build(
                "Subject",
                "from@example.com",
                "reply@example.com",
                new SlideHtmlRenderer.Result("<p>Hello</p>", Map.of(), "Hello"),
                item);
    var message =
        new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(bytes));
    assertEquals("to@example.com", message.getRecipients(Message.RecipientType.TO)[0].toString());
    assertEquals("cc@example.com", message.getRecipients(Message.RecipientType.CC)[0].toString());
    assertEquals("bcc@example.com", message.getRecipients(Message.RecipientType.BCC)[0].toString());
    assertEquals("Subject", message.getSubject());
    assertTrue(message.isMimeType("multipart/*"));
  }
}
