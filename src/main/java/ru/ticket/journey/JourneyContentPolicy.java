package ru.ticket.journey;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import ru.ticket.common.ApiException;

/** Content validation and lifecycle rules, independent from persistence and HTTP. */
@Component
public class JourneyContentPolicy {
  public record Lesson(
      String id, String date, String time, String title, String location, Integer durationMinutes) {
    public int duration() {
      return durationMinutes == null ? 60 : durationMinutes;
    }
  }

  public record Status(String state, Instant usedAt, String validUntil, boolean consumable) {}

  private final ObjectMapper mapper;

  public JourneyContentPolicy(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public void validate(JourneyService.Kind kind, Map<String, String> data) {
    if (kind == JourneyService.Kind.POETRY && !data.getOrDefault("poetry", "").matches("[a-f0-9]{64}"))
      throw ApiException.badRequest("journey.poetry.preferences");
    if (Set.of(JourneyService.Kind.PRICETAG, JourneyService.Kind.PROPERTY).contains(kind))
      JourneyMoney.formatted(data);
    String until = data.getOrDefault("valid_until", "").strip();
    try {
      ZoneId.of(data.getOrDefault("timezone", "Europe/Moscow"));
      if (!until.isEmpty()) {
        var date = LocalDate.parse(until);
        if (date.getYear() < 1900 || date.getYear() > 2200)
          throw ApiException.badRequest("Validity year must be between 1900 and 2200");
      }
      if (kind == JourneyService.Kind.GIFT
          && new BigDecimal(data.getOrDefault("amount", "0")).signum() <= 0)
        throw ApiException.badRequest("Specify a positive gift value");
      if (Set.of(JourneyService.Kind.TICKET, JourneyService.Kind.GIFT, JourneyService.Kind.PASS)
              .contains(kind)
          && until.isEmpty()) throw ApiException.badRequest("Specify the validity date");
    } catch (DateTimeException | NumberFormatException error) {
      throw ApiException.badRequest("Check the validity date, timezone and gift value");
    }
    for (String field : List.of("portfolio", "website")) {
      String value = data.getOrDefault(field, "").strip();
      if (value.isEmpty()) continue;
      try {
        URI url = URI.create(value);
        if (!Set.of("https", "http").contains(url.getScheme())
            || url.getHost() == null
            || url.getUserInfo() != null)
          throw ApiException.badRequest("Use an http or https website address");
      } catch (IllegalArgumentException error) {
        throw ApiException.badRequest("Check the website address");
      }
    }
    if (kind == JourneyService.Kind.SCHEDULE) lessons(data);
  }

  public List<Lesson> lessons(Map<String, String> data) {
    try {
      Lesson[] values = mapper.readValue(data.getOrDefault("events", "[]"), Lesson[].class);
      if (values == null || values.length == 0 || values.length > 100)
        throw new IllegalArgumentException();
      var ids = new java.util.HashSet<String>();
      for (Lesson lesson : values) {
        if (lesson == null
            || lesson.id() == null
            || !lesson.id().matches("[A-Za-z0-9-]{1,40}")
            || !ids.add(lesson.id())
            || lesson.title() == null
            || lesson.title().isBlank()
            || lesson.title().length() > 200
            || lesson.location() == null
            || lesson.location().length() > 200
            || lesson.duration() < 1
            || lesson.duration() > 1440) throw new IllegalArgumentException();
        LocalDate.parse(lesson.date());
        LocalTime.parse(lesson.time());
      }
      return List.of(values);
    } catch (Exception error) {
      throw ApiException.badRequest("Add up to 100 lessons with a date, time and title");
    }
  }

  public Status status(JourneyEntity entity, Map<String, String> data, Instant now) {
    String until = data.getOrDefault("valid_until", "");
    String state = entity.revoked ? "REVOKED" : entity.usedAt != null ? "USED" : "ACTIVE";
    if (state.equals("ACTIVE") && !until.isBlank()) {
      Instant end =
          LocalDate.parse(until)
              .plusDays(1)
              .atStartOfDay(ZoneId.of(data.getOrDefault("timezone", "Europe/Moscow")))
              .toInstant();
      if (!now.isBefore(end)) state = "EXPIRED";
    }
    return new Status(
        state, entity.usedAt, until, JourneyService.Kind.valueOf(entity.kind).consumable());
  }
}
