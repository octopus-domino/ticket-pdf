package ru.ticket.email;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.ticket.dto.Slide;

import java.util.List;
import java.util.Map;

public final class EmailDtos {

    private EmailDtos() { }

    public record EmailOptions(
            @Email String from,
            String replyTo,
            String subject
    ) { }

    public static class EmailItem {
        public String dataModelId;
        public Map<String, Object> inlineValues;
        public String filenameOverride;
        @Email @NotBlank public String to;
        public List<@Email String> cc;
        public List<@Email String> bcc;
        public String subject;
    }

    public static class EmailRequest {
        public String templateId;
        @Valid public List<Slide> inlineSlides;
        @NotNull @Valid public List<EmailItem> items;
        @Valid public EmailOptions email = new EmailOptions(null, null, null);
        public String filenamePattern;
    }
}