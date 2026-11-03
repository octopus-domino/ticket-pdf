package ru.ticket.generation.domain;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разворачивает {{path}} в имени файла + санитизирует. NFR-SEC-03: убираем ../, \0,
 * control-символы.
 */
public final class FilenameTemplate {

  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([\\w.]+)\\s*\\}\\}");
  private static final Pattern UNSAFE = Pattern.compile("[\\\\/:*?\"<>|\\x00-\\x1F]");
  private static final int MAX_LEN = 200;

  private FilenameTemplate() {}

  public static String resolve(String pattern, Map<String, Object> values, String fallback) {
    String p = (pattern == null || pattern.isBlank()) ? fallback : pattern;
    Matcher m = PLACEHOLDER.matcher(p);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      Object v = values == null ? null : ModelValues.get(values, m.group(1));
      String replacement = v == null ? "" : String.valueOf(v);
      m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
    }
    m.appendTail(sb);

    String safe = UNSAFE.matcher(sb.toString()).replaceAll("_").trim();
    if (safe.isEmpty()) safe = fallback;
    if (safe.length() > MAX_LEN) safe = safe.substring(0, MAX_LEN);
    // защита от ../ и подобного
    safe = safe.replace("..", "_");
    return safe;
  }
}
