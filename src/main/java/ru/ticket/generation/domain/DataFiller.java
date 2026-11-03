package ru.ticket.generation.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ru.ticket.dto.*;

/**
 * Чистая функция: подставляет значения в Slide. Поддерживает: {{event.title}},
 * {{event.items[0].code}} — скаляры в Text/Shape/Footer/Image/Table PlaceholderElement — sample из
 * пути TEXT_PLACEHOLDER ("Введите текст") — очищается, чтобы пустые элементы не появлялись в
 * готовом документе.
 */
public final class DataFiller {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([\\w.\\[\\]]+)\\s*\\}\\}");

  /** Должно совпадать с frontend shared/constants.TEXT_PLACEHOLDER. */
  private static final String TEXT_PLACEHOLDER = "Введите текст";

  private DataFiller() {}

  public static Slide fill(Slide template, Map<String, Object> values) {
    return fill(template, values, List.of());
  }

  public static Slide fill(
      Slide template,
      Map<String, Object> values,
      List<ru.ticket.template.domain.BindingDto> bindings) {
    Slide copy = deepCopy(template);
    if (copy.elements == null) return copy;
    Map<String, String> strValues = flatten(values);
    for (Element el : copy.elements) {
      Map<String, String> local = new HashMap<>(strValues);
      for (var binding : bindings) {
        if (!Objects.equals(binding.elementId, el.id) || "ignored".equals(binding.status)) continue;
        String source =
            binding.placeholder == null
                ? ""
                : binding.placeholder.replace("{{", "").replace("}}", "").trim();
        Object replacement = ModelValues.get(values, binding.schemaPath);
        if (replacement != null) {
          local.put(source, String.valueOf(replacement));
          for (var entry : strValues.entrySet()) {
            if (entry.getKey().startsWith(binding.schemaPath + "[")
                || entry.getKey().startsWith(binding.schemaPath + "."))
              local.put(
                  source + entry.getKey().substring(binding.schemaPath.length()), entry.getValue());
          }
        }
      }
      applyToElement(el, local);
      if (el instanceof ChartElement chart && chart.dataPath != null) {
        Object rows = ModelValues.get(values, chart.dataPath);
        if (rows instanceof List<?> list)
          chart.values =
              list.stream()
                  .map(
                      row ->
                          chart.valueField == null || chart.valueField.isBlank()
                              ? row
                              : ModelValues.get(row, chart.valueField))
                  .map(value -> Double.valueOf(String.valueOf(value)))
                  .filter(Double::isFinite)
                  .toList();
      }
    }
    return copy;
  }

  private static Slide deepCopy(Slide s) {
    return MAPPER.convertValue(MAPPER.valueToTree(s), Slide.class);
  }

  /* ============================================================
  Flatten — рекурсивно с array-index: event.items[0].code
  ============================================================ */

  public static Map<String, String> flatten(Map<String, Object> src) {
    Map<String, String> out = new HashMap<>();
    if (src == null) return out;
    flattenInto("", src, out);
    return out;
  }

  private static void flattenInto(String prefix, Map<String, Object> src, Map<String, String> out) {
    for (Map.Entry<String, Object> e : src.entrySet()) {
      String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
      flattenValue(key, e.getValue(), out);
    }
  }

  private static void flattenValue(String key, Object val, Map<String, String> out) {
    if (val == null) return;
    if (val instanceof Map) {
      for (Map.Entry<?, ?> e : ((Map<?, ?>) val).entrySet()) {
        flattenValue(key + "." + e.getKey(), e.getValue(), out);
      }
    } else if (val instanceof List) {
      List<?> list = (List<?>) val;
      for (int i = 0; i < list.size(); i++) {
        flattenValue(key + "[" + i + "]", list.get(i), out);
      }
    } else {
      out.put(key, String.valueOf(val));
    }
  }

  /* ============================================================
  Apply
  ============================================================ */

  private static void applyToElement(Element el, Map<String, String> values) {
    if (el == null) return;

    if (el instanceof TextElement t) {
      t.text = replacePlaceholders(t.text, values);
      t.html = replaceHtml(t.html, values);
      clearPlaceholder(t);
    } else if (el instanceof ShapeElement sh) {
      // ГЛАВНОЕ ИСПРАВЛЕНИЕ — раньше shape пропускался
      sh.text = replacePlaceholders(sh.text, values);
    } else if (el instanceof PlaceholderElement p) {
      if (p.fieldPath != null && values.containsKey(p.fieldPath)) {
        p.sample = values.get(p.fieldPath);
      }
    } else if (el instanceof ImageElement img) {
      img.src = replacePlaceholders(img.src, values);
    } else if (el instanceof FooterElement f) {
      f.text = replacePlaceholders(f.text, values);
      f.html = replaceHtml(f.html, values);
      clearPlaceholder(f);
    } else if (el instanceof QrElement qr) {
      qr.text = replacePlaceholders(qr.text, values);
    } else if (el instanceof StampElement stamp) {
      stamp.topText = replacePlaceholders(stamp.topText, values);
      stamp.centerText = replacePlaceholders(stamp.centerText, values);
      stamp.bottomText = replacePlaceholders(stamp.bottomText, values);
      for (var text :
          stamp.texts == null ? java.util.List.<ru.ticket.dto.StampTextItem>of() : stamp.texts)
        text.text = replacePlaceholders(text.text, values);
      for (var image :
          stamp.images == null ? java.util.List.<ru.ticket.dto.StampImageItem>of() : stamp.images)
        image.src = replacePlaceholders(image.src, values);
    } else if (el instanceof TableElement tb) {
      if (tb.data == null) return;
      for (List<String> row : tb.data) {
        if (row == null) continue;
        for (int i = 0; i < row.size(); i++) {
          row.set(i, replacePlaceholders(row.get(i), values));
        }
      }
    }
  }

  /** Убирает «Введите текст» / пустой html, чтобы placeholder-текст не попадал в готовый PDF. */
  private static void clearPlaceholder(TextElement t) {
    if (isEmptyHtml(t.html) && isEmptyText(t.text)) {
      t.text = "";
      t.html = null;
    }
  }

  private static void clearPlaceholder(FooterElement f) {
    if (isEmptyHtml(f.html) && isEmptyText(f.text)) {
      f.text = "";
      f.html = null;
    }
  }

  private static boolean isEmptyText(String s) {
    if (s == null) return true;
    String t = s.trim();
    return t.isEmpty() || t.equals(TEXT_PLACEHOLDER);
  }

  private static boolean isEmptyHtml(String h) {
    if (h == null) return true;
    String t = h.replaceAll("<[^>]+>", "").trim();
    return t.isEmpty() || t.equals(TEXT_PLACEHOLDER);
  }

  private static String replaceHtml(String text, Map<String, String> values) {
    Map<String, String> escaped = new HashMap<>();
    values.forEach((key, value) -> escaped.put(key, ru.ticket.util.XmlEscape.escape(value)));
    return replacePlaceholders(text, escaped);
  }

  static String replacePlaceholders(String text, Map<String, String> values) {
    if (text == null || text.isEmpty()) return text;
    Matcher m = PLACEHOLDER.matcher(text);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      String path = m.group(1);
      String replacement = values.get(path);
      if (replacement == null) {
        replacement = ""; // маркер, что значения нет
      }
      m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  public static List<String> unresolvedPaths(Slide filled) {
    List<String> out = new ArrayList<>();
    if (filled.elements == null) return out;
    for (Element el : filled.elements) scan(el, out);
    return out;
  }

  private static void scan(Element el, List<String> out) {
    StringBuilder text = new StringBuilder();
    if (el instanceof TextElement t) {
      text.append(safe(t.text)).append(' ').append(safe(t.html));
    } else if (el instanceof ShapeElement sh) {
      text.append(safe(sh.text));
    } else if (el instanceof FooterElement f) {
      text.append(safe(f.text)).append(' ').append(safe(f.html));
    } else if (el instanceof ImageElement img) {
      text.append(safe(img.src));
    } else if (el instanceof TableElement tb && tb.data != null) {
      for (List<String> row : tb.data) {
        if (row == null) continue;
        for (String c : row) text.append(safe(c)).append(' ');
      }
    } else {
      return;
    }
    Matcher m = PLACEHOLDER.matcher(text.toString());
    while (m.find()) out.add(m.group(1));
  }

  private static String safe(String s) {
    return s == null ? "" : s;
  }
}
