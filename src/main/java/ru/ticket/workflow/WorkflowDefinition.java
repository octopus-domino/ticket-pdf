package ru.ticket.workflow;

import java.util.*;
import ru.ticket.common.ApiException;
import ru.ticket.generation.domain.ModelValues;

/** Versioned, declarative form contract; it cannot execute scripts or arbitrary server actions. */
public record WorkflowDefinition(
    int version,
    String slug,
    String title,
    String description,
    String templateId,
    List<Step> steps,
    String completionMessage,
    Attendance attendance) {
  public WorkflowDefinition(int version, String slug, String title, String description, String templateId, List<Step> steps, String completionMessage) {
    this(version, slug, title, description, templateId, steps, completionMessage, null);
  }
  public record Attendance(String title, String instructions) {}
  public record Step(String title, List<Field> fields) {}

  public record Field(
      String path, String label, String type, boolean required, List<String> options) {}

  public void validate() {
    if (attendance != null && (attendance.title() == null || attendance.title().isBlank() || attendance.title().length() > 120
        || attendance.instructions() == null || attendance.instructions().isBlank() || attendance.instructions().length() > 300))
      throw ApiException.badRequest("Invalid attendance step");
    if (version != 1
        || slug == null
        || !slug.matches("[a-z][a-z0-9-]{1,46}[a-z0-9]")
        || title == null
        || title.isBlank()
        || title.length() > 120
        || description == null
        || description.length() > 600
        || templateId == null
        || templateId.isBlank()
        || steps == null
        || steps.isEmpty()
        || steps.size() > 8
        || completionMessage == null
        || completionMessage.length() > 500)
      throw ApiException.badRequest("Проверьте название, адрес, шаблон и шаги сценария");
    Set<String> paths = new HashSet<>();
    for (var step : steps) {
      if (step == null
          || step.title() == null
          || step.title().isBlank()
          || step.title().length() > 80
          || step.fields() == null
          || step.fields().isEmpty()
          || step.fields().size() > 30)
        throw ApiException.badRequest("В каждом шаге должно быть от 1 до 30 полей");
      for (var field : step.fields()) {
        if (field == null
            || field.path() == null
            || field.label() == null
            || field.label().isBlank()
            || field.label().length() > 100
            || field.type() == null
            || !Set.of("text", "textarea", "number", "date", "email", "select", "boolean")
                .contains(field.type())
            || field.path().equals("visit_url")
            || !paths.add(field.path())
            || paths.size() > 100)
          throw ApiException.badRequest("Проверьте поля сценария: пути не должны повторяться");
        try {
          ModelValues.validatePath(field.path());
        } catch (IllegalArgumentException error) {
          throw ApiException.badRequest("Недопустимый путь поля");
        }
        if (field.type().equals("select")
            && (field.options() == null
                || field.options().isEmpty()
                || field.options().size() > 30
                || field.options().stream()
                    .anyMatch(o -> o == null || o.isBlank() || o.length() > 120)))
          throw ApiException.badRequest("Добавьте варианты выбора");
      }
    }
  }

  public Map<String, Object> values(Map<String, Object> input) {
    if (input == null || input.size() > 100)
      throw ApiException.badRequest("Проверьте данные формы");
    Map<String, Object> result = new LinkedHashMap<>();
    for (var step : steps)
      for (var field : step.fields()) {
        Object raw = input.get(field.path());
        if (raw == null || raw instanceof String s && s.isBlank()) {
          if (field.required()) throw ApiException.badRequest("Заполните: " + field.label());
          continue;
        }
        if (!(raw instanceof String || raw instanceof Number || raw instanceof Boolean)
            || raw.toString().length() > 4000)
          throw ApiException.badRequest("Проверьте: " + field.label());
        String value = raw.toString().strip();
        try {
          Object parsed =
              switch (field.type()) {
                case "number" -> new java.math.BigDecimal(value.replace(',', '.'));
                case "date" -> java.time.LocalDate.parse(value).toString();
                case "boolean" -> {
                  if (!Set.of("true", "false").contains(value)
                      || field.required() && !value.equals("true"))
                    throw new IllegalArgumentException();
                  yield Boolean.valueOf(value);
                }
                case "email" -> {
                  if (!value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
                    throw new IllegalArgumentException();
                  yield value;
                }
                case "select" -> {
                  if (!field.options().contains(value)) throw new IllegalArgumentException();
                  yield value;
                }
                default -> value;
              };
          if (parsed instanceof java.math.BigDecimal number
              && (number.precision() > 20 || Math.abs(number.scale()) > 8))
            throw new IllegalArgumentException();
          result.put(field.path(), parsed);
        } catch (RuntimeException error) {
          throw ApiException.badRequest("Проверьте: " + field.label());
        }
      }
    if (!result.keySet().containsAll(input.keySet())) {
      var allowed =
          steps.stream()
              .flatMap(s -> s.fields().stream())
              .map(Field::path)
              .collect(java.util.stream.Collectors.toSet());
      if (!allowed.containsAll(input.keySet()))
        throw ApiException.badRequest("Форма содержит неизвестные поля");
    }
    return result;
  }
}
