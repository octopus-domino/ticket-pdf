package ru.ticket.email;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.Properties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import ru.ticket.email.render.SlideHtmlRenderer;

/**
 * Builds an RFC-822 MIME message (.eml) with the rendered slide attached as an inline CID image.
 *
 * <p>No data-URL → CID substitution happens here: the renderer emits {@code <img
 * src="cid:slide-1">} directly and puts the PNG under the same key in {@link
 * SlideHtmlRenderer.Result#inlineImages()}.
 */
@Component
public class EmlBuilder {

  public byte[] build(
      String subject, String from, String replyTo, SlideHtmlRenderer.Result rendered)
      throws Exception {
    return build(subject, from, replyTo, rendered, null);
  }

  public byte[] build(
      String subject,
      String from,
      String replyTo,
      SlideHtmlRenderer.Result rendered,
      EmailDtos.EmailItem recipient)
      throws Exception {

    Session session = Session.getInstance(new Properties());
    MimeMessage msg = new MimeMessage(session);
    MimeMessageHelper h =
        new MimeMessageHelper(msg, MimeMessageHelper.MULTIPART_MODE_RELATED, "UTF-8");

    h.setFrom(from != null && !from.isBlank() ? from : "noreply@ticket-editor.local");
    h.setSubject(subject == null || subject.isBlank() ? "Документ" : subject);
    if (replyTo != null && !replyTo.isBlank()) h.setReplyTo(replyTo);
    if (recipient != null) {
      h.setTo(recipient.to);
      if (recipient.cc != null && !recipient.cc.isEmpty())
        h.setCc(recipient.cc.toArray(String[]::new));
      if (recipient.bcc != null && !recipient.bcc.isEmpty())
        h.setBcc(recipient.bcc.toArray(String[]::new));
    }

    h.setText(rendered.plainText(), rendered.html());

    for (Map.Entry<String, byte[]> e : rendered.inlineImages().entrySet()) {
      h.addInline(e.getKey(), new ByteArrayResource(e.getValue()), "image/png");
    }

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    msg.writeTo(out);
    return out.toByteArray();
  }
}
