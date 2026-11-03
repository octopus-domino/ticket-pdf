package ru.ticket.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class SmtpConfigurationTest {
  private final ApplicationContextRunner context = new ApplicationContextRunner()
      .withUserConfiguration(MailSenderAutoConfiguration.class)
      .withInitializer(application -> {
        try {
          for (var source : new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))) {
            application.getEnvironment().getPropertySources().addLast(source);
          }
        } catch (java.io.IOException error) {
          throw new UncheckedIOException(error);
        }
      })
      .withPropertyValues("SMTP_HOST=smtp.example.test", "SMTP_USER=sender@example.test", "SMTP_PASSWORD=test-only");

  @Test
  void configuresAuthenticatedSmtpOverSslAndFiniteTimeoutsWithoutConnecting() {
    context.run(application -> {
      assertThat(application).hasNotFailed();
      var sender = application.getBean(JavaMailSenderImpl.class);
      assertThat(sender.getProtocol()).isEqualTo("smtp");
      assertThat(sender.getPort()).isEqualTo(465);
      assertThat(sender.getJavaMailProperties()).containsEntry("mail.smtp.auth", "true")
          .containsEntry("mail.smtp.ssl.enable", "true")
          .containsEntry("mail.smtp.connectiontimeout", "10000")
          .containsEntry("mail.smtp.timeout", "30000")
          .containsEntry("mail.smtp.writetimeout", "30000");
      assertThat(application.getEnvironment().getProperty("app.email.default-from")).isEqualTo("sender@example.test");
    });
  }

  @Test
  void allowsStartTlsWithSslDisabled() {
    context.withPropertyValues("SMTP_PORT=587", "SMTP_SSL=false", "SMTP_TLS=true").run(application -> {
      var sender = application.getBean(JavaMailSenderImpl.class);
      assertThat(sender.getPort()).isEqualTo(587);
      assertThat(sender.getJavaMailProperties()).containsEntry("mail.smtp.ssl.enable", "false")
          .containsEntry("mail.smtp.starttls.enable", "true")
          .containsEntry("mail.smtp.starttls.required", "true");
    });
  }
}
