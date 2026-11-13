package ru.ticket.journey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import java.util.regex.Pattern;

/** Conservative weekly subset. Unsupported seasonal/holiday/solar rules remain unknown. */
final class OsmOpeningHours {
  private static final List<String> DAYS = List.of("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su");
  private static final List<String> KEYS = List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun");
  private static final String TIME = "(?:[01][0-9]|2[0-3]):[0-5][0-9]-(?:(?:[01][0-9]|2[0-3]):[0-5][0-9]|24:00)";
  private static final Pattern RULE = Pattern.compile("^(Mo|Tu|We|Th|Fr|Sa|Su)(?:[-,](?:Mo|Tu|We|Th|Fr|Sa|Su))*$");
  static ObjectNode parse(String text, ObjectMapper mapper) {
    var result = mapper.createObjectNode();
    if (text == null || text.isBlank() || text.length() > 300) return result;
    String value = text.strip();
    if (value.equals("24/7")) value = "Mo-Su 00:00-24:00";
    for (String rule : value.split(";", -1)) {
      String[] parts = rule.strip().split("\\s+", 2);
      String selector = parts.length == 1 ? "Mo-Su" : parts[0];
      String hours = parts.length == 1 ? parts[0] : parts[1];
      if (!RULE.matcher(selector).matches() || !(hours.equals("off") || hours.matches(TIME + "(?:," + TIME + ")*"))) return mapper.createObjectNode();
      Set<Integer> days = new LinkedHashSet<>();
      for (String token : selector.split(",")) {
        String[] range = token.split("-");
        if (range.length > 2) return mapper.createObjectNode();
        int start = DAYS.indexOf(range[0]), end = DAYS.indexOf(range[range.length - 1]);
        if (start < 0 || end < 0) return mapper.createObjectNode();
        for (int day = start; ; day = (day + 1) % 7) { days.add(day); if (day == end) break; }
      }
      var intervals = mapper.createArrayNode();
      if (!hours.equals("off")) for (String interval : hours.split(",")) {
        String[] times = interval.split("-");
        if (times[1].compareTo(times[0]) <= 0) return mapper.createObjectNode();
        intervals.add(mapper.createObjectNode().put("from", times[0]).put("to", times[1]));
      }
      // Later rules for a weekday override its previous regular hours.
      for (int day : days) result.set(KEYS.get(day), mapper.createObjectNode().set("working_hours", intervals.deepCopy()));
    }
    return result;
  }
}
