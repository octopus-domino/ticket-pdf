package ru.ticket.journey;

import java.time.*;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
import ru.ticket.common.ApiException;

/** Standards-based downloads with escaped, folded text; no spreadsheet or PDF engine involved. */
@Component
public class JourneyAttachments {
  private final JourneyContentPolicy policy;

  public JourneyAttachments(JourneyContentPolicy policy) {
    this.policy = policy;
  }

  public String calendar(JourneyService.View view) {
    if (view.kind() != JourneyService.Kind.SCHEDULE)
      throw ApiException.badRequest("Not a schedule");
    StringBuilder out =
        new StringBuilder(
            "BEGIN:VCALENDAR\r\n"
                + "VERSION:2.0\r\n"
                + "PRODID:-//CoDoc//Schedule//RU\r\n"
                + "CALSCALE:GREGORIAN\r\n");
    var utc = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    var zone = ZoneId.of(view.data().getOrDefault("timezone", "Europe/Moscow"));
    for (var lesson : policy.lessons(view.data())) {
      var start =
          LocalDate.parse(lesson.date())
              .atTime(LocalTime.parse(lesson.time()))
              .atZone(zone)
              .toInstant();
      out.append("BEGIN:VEVENT\r\n");
      line(out, "UID", view.id() + "-" + lesson.id() + "@codoc.site");
      line(out, "DTSTAMP", utc.format(view.updatedAt()));
      line(out, "DTSTART", utc.format(start));
      line(out, "DTEND", utc.format(start.plusSeconds(lesson.duration()*60L)));
      line(out, "SUMMARY", escape(lesson.title()));
      line(out, "LOCATION", escape(lesson.location()));
      out.append("END:VEVENT\r\n");
    }
    return out.append("END:VCALENDAR\r\n").toString();
  }

  public String contact(JourneyService.View view) {
    if (view.kind() != JourneyService.Kind.BADGE) throw ApiException.badRequest("Not a badge");
    var out = new StringBuilder("BEGIN:VCARD\r\nVERSION:3.0\r\n");
    line(out, "FN", escape(view.data().getOrDefault("recipient", view.title())));
    for (var pair :
        new String[][] {{"ORG", "issuer"}, {"TITLE", "role"}, {"TEL", "phone"}, {"EMAIL", "email"}})
      line(out, pair[0], escape(view.data().getOrDefault(pair[1], "")));
    return out.append("END:VCARD\r\n").toString();
  }

  private static String escape(String text) {
    return text.replace("\\", "\\\\")
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .replace("\n", "\\n")
        .replace(";", "\\;")
        .replace(",", "\\,");
  }

  private static void line(StringBuilder out, String key, String value) {
    int bytes = 0;
    String content = key + ":" + value;
    for (int cp : content.codePoints().toArray()) {
      String next = new String(Character.toChars(cp));
      int length = next.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
      if (bytes + length > 73) {
        out.append("\r\n ");
        bytes = 1;
      }
      out.append(next);
      bytes += length;
    }
    out.append("\r\n");
  }
}
