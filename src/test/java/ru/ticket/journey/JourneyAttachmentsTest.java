package ru.ticket.journey;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JourneyAttachmentsTest {
  private final JourneyAttachments attachments =
      new JourneyAttachments(new JourneyContentPolicy(new ObjectMapper()));

  @Test
  void calendarUsesStableIdsUtcAndEscapesInjectedLines() {
    var view =
        new JourneyService.View(
            "Abc123Def456",
            JourneyService.Kind.SCHEDULE,
            "Group",
            Map.of(
                "timezone",
                "Europe/Moscow",
                "events",
                "[{\"id\":\"lesson-1\",\"date\":\"2026-12-01\",\"time\":\"12:00\",\"title\":\"Math\\n"
                    + "END:VEVENT\",\"location\":\"Room,1\"}]"),
            Instant.parse("2026-11-01T00:00:00Z"));
    String calendar = attachments.calendar(view);
    assertThat(calendar)
        .contains(
            "UID:Abc123Def456-lesson-1@codoc.site\r\n",
            "DTSTART:20261201T090000Z",
            "SUMMARY:Math\\nEND:VEVENT",
            "LOCATION:Room\\,1");
    assertThat(calendar.split("\r\nEND:VEVENT\r\n", -1)).hasSize(2);
  }

  @Test
  void vcardEscapesContactTextAndFoldsUtf8Lines() {
    String contact =
        attachments.contact(
            new JourneyService.View(
                "A",
                JourneyService.Kind.BADGE,
                "Event",
                Map.of("recipient", "Анна".repeat(30), "phone", "123\r\nEND:VCARD"),
                Instant.now()));
    assertThat(contact).contains("TEL:123\\nEND:VCARD\r\n");
    for (String line : contact.split("\r\n"))
      assertThat(line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
          .isLessThanOrEqualTo(73);
  }
}
