package ru.ticket.email;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import ru.ticket.email.EmailDtos.EmailItem;
import ru.ticket.email.EmailDtos.EmailOptions;
import ru.ticket.email.render.SlideHtmlRenderer;

import java.util.Map;

@Service
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final JavaMailSender mail;
    private final String defaultFrom;

    public EmailSender(JavaMailSender mail,
                       @Value("${app.email.default-from:noreply@ticket-editor.local}")
                       String defaultFrom) {
        this.mail = mail;
        this.defaultFrom = defaultFrom;
    }

    public void send(EmailItem item,
                     EmailOptions opts,
                     String resolvedSubject,
                     SlideHtmlRenderer.Result rendered) throws Exception {

        MimeMessage msg = mail.createMimeMessage();
        MimeMessageHelper h = new MimeMessageHelper(
                msg, MimeMessageHelper.MULTIPART_MODE_RELATED, "UTF-8");

        h.setFrom(opts != null && opts.from() != null && !opts.from().isBlank()
                ? opts.from() : defaultFrom);

        h.setTo(item.to);
        if (item.cc != null && !item.cc.isEmpty()) h.setCc(item.cc.toArray(String[]::new));
        if (item.bcc != null && !item.bcc.isEmpty()) h.setBcc(item.bcc.toArray(String[]::new));
        if (opts != null && opts.replyTo() != null && !opts.replyTo().isBlank()) {
            h.setReplyTo(opts.replyTo());
        }

        String subject = resolvedSubject != null && !resolvedSubject.isBlank()
                ? resolvedSubject
                : (item.subject != null && !item.subject.isBlank()
                ? item.subject : "Документ");
        h.setSubject(subject);

        h.setText(rendered.plainText(), rendered.html());

        for (Map.Entry<String, byte[]> e : rendered.inlineImages().entrySet()) {
            h.addInline(e.getKey(), new ByteArrayResource(e.getValue()), "image/png");
        }

        mail.send(msg);
        log.info("email.sent to={} subject={}", item.to, subject);
    }
}