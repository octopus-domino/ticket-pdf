package ru.ticket.email;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import ru.ticket.dto.Slide;

public final class EmailDtos {

  private EmailDtos() {}

  public record EmailOptions(@Email String from, @Email String replyTo, String subject) {}

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
    @jakarta.validation.constraints.Size(max = 100)
    public String requestId;

    public String templateId;

    @Valid
    @jakarta.validation.constraints.Size(max = 50)
    public List<Slide> inlineSlides;

    @NotNull
    @Valid
    @jakarta.validation.constraints.Size(min = 1, max = 100)
    public List<EmailItem> items;

    @Valid public EmailOptions email = new EmailOptions(null, null, null);
    public String filenamePattern;
  }
}
