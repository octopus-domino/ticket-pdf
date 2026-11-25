package ru.ticket.generation.domain;

import java.time.LocalDate;
import java.util.*;
import ru.ticket.schema.domain.FieldDef;

/** Shared validation for CRUD, imports and rendering; defaults never mutate stored models. */
public final class ModelValues {
  private ModelValues() {}

  public static void validatePath(String path) {
    if (path == null || path.length() > 1000 || !path.matches("[\\w]+(?:\\.[\\w]+|\\[\\d{1,4}])*"))
      throw new IllegalArgumentException("Invalid field path");
    for (String part : path.replaceAll("\\[(\\d+)]", ".$1").split("\\."))
      if (Set.of("__proto__", "prototype", "constructor").contains(part))
        throw new IllegalArgumentException("Unsafe field path");
  }

  public static Object get(Object values, String path) {
    validatePath(path);
    if (values instanceof Map<?, ?> map && map.containsKey(path)) return map.get(path);
    Object current = values;
    for (String part : path.replaceAll("\\[(\\d+)]", ".$1").split("\\.")) {
      if (current instanceof Map<?, ?> map) current = map.get(part);
      else if (current instanceof List<?> list && part.matches("\\d+")) {
        int index = Integer.parseInt(part);
        current = index < list.size() ? list.get(index) : null;
      } else return null;
    }
    return current;
  }

  public static Map<String, Object> validate(Map<String, Object> values, List<FieldDef> fields) {
    if (values == null || values.size() > 500)
      throw new IllegalArgumentException("At most 500 model fields required");
    Map<String, Object> resolved = new LinkedHashMap<>(values);
    for (FieldDef field : fields) {
      validatePath(field.path());
      Object value = get(resolved, field.path());
      if (value == null && field.defaultValue() != null) {
        value = field.defaultValue();
        resolved.put(field.path(), value);
      }
      if (value == null || value instanceof String s && s.isBlank()) {
        // Child fields of an array are checked separately for every row.
        FieldDef array =
            fields.stream()
                .filter(f -> f.type().equals("array") && field.path().startsWith(f.path() + "."))
                .findFirst()
                .orElse(null);
        if (array != null) {
          Object rows = get(resolved, array.path());
          if (rows instanceof List<?> list)
            for (Object row : list)
              validateRow(row, field, field.path().substring(array.path().length() + 1));
        } else if (field.required())
          throw new IllegalArgumentException(field.path() + ": required field is empty");
      } else checkType(value, field);
    }
    return resolved;
  }

  private static void validateRow(Object row, FieldDef field, String path) {
    Object value = get(row, path);
    if (value == null || value instanceof String s && s.isBlank()) {
      if (field.required())
        throw new IllegalArgumentException(field.path() + ": required array field is empty");
    } else checkType(value, field);
  }

  private static void checkType(Object value, FieldDef field) {
    boolean valid;
    try {
      valid =
          switch (field.type()) {
            case "number" -> Double.isFinite(Double.parseDouble(value.toString()));
            case "boolean" ->
                value instanceof Boolean || Set.of("true", "false").contains(value.toString());
            case "date" -> {
              LocalDate.parse(value.toString());
              yield true;
            }
            case "array" -> value instanceof List<?> list && (!field.required() || !list.isEmpty());
            case "object" -> value instanceof Map<?, ?>;
            case "string", "image" -> value instanceof String;
            default -> false;
          };
    } catch (RuntimeException error) {
      valid = false;
    }
    if (!valid) throw new IllegalArgumentException(field.path() + ": invalid " + field.type());
  }
}
