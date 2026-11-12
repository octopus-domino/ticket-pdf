package ru.ticket.journey;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import ru.ticket.common.ApiException;

/** Snapshot the author's field captions so preview and issued PDFs keep identical copy. */
final class JourneyFieldLabels {
  private static final ObjectMapper JSON = new ObjectMapper();

  static Map<String, String> read(String value) {
    if (value == null || value.isBlank()) return Map.of();
    if (value.length() > 3000) throw ApiException.badRequest("Invalid field captions");
    try {
      var node = JSON.readTree(value);
      if (!node.isObject() || node.size() > 20)
        throw ApiException.badRequest("Invalid field captions");
      var result = new LinkedHashMap<String, String>();
      node.fields()
          .forEachRemaining(
              item -> {
                if (!item.getKey().matches("[a-z][a-z0-9_]{0,31}")
                    || Set.of("__proto__", "constructor", "prototype").contains(item.getKey())
                    || !item.getValue().isTextual()
                    || item.getValue().asText().length() > 80)
                  throw ApiException.badRequest("Invalid field captions");
                result.put(item.getKey(), item.getValue().asText());
              });
      return result;
    } catch (java.io.IOException error) {
      throw ApiException.badRequest("Invalid field captions");
    }
  }
}
