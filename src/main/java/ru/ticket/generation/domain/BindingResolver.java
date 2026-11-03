package ru.ticket.generation.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ru.ticket.dto.Element;
import ru.ticket.dto.ImageElement;
import ru.ticket.dto.PlaceholderElement;
import ru.ticket.dto.Slide;
import ru.ticket.dto.TextElement;
import ru.ticket.template.domain.BindingDto;

/**
 * Чистая функция: обходит слайды и собирает все потенциальные точки вставки. Не знает про схему —
 * просто находит все {{...}} и fieldPath.
 */
public final class BindingResolver {

  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([\\w.]+)\\s*\\}\\}");

  private BindingResolver() {}

  public static List<BindingDto> resolve(List<Slide> slides) {
    Map<String, BindingDto> out = new LinkedHashMap<>();
    for (Slide slide : slides) {
      if (slide.elements == null) continue;
      for (Element el : slide.elements) {
        collectFrom(el, out);
      }
    }
    return new ArrayList<>(out.values());
  }

  private static void collectFrom(Element el, Map<String, BindingDto> out) {
    if (el == null || el.id == null) return;
    String elementId = el.id;

    if (el instanceof TextElement t) {
      collectFromText(elementId, t.text, "text", out);
      collectFromText(elementId, t.html, "text", out);
    } else if (el instanceof PlaceholderElement p) {
      if (p.fieldPath != null && !p.fieldPath.isBlank()) {
        out.putIfAbsent(
            elementId + "|" + p.fieldPath,
            new BindingDto(
                elementId,
                "Placeholder",
                p.fieldPath,
                p.fieldPath,
                "legacy",
                "linked",
                p.fieldPath));
      }
    } else if (el instanceof ImageElement img) {
      collectFromText(elementId, img.src, "image", out);
    }
  }

  private static void collectFromText(
      String elementId, String text, String source, Map<String, BindingDto> out) {
    if (text == null || text.isEmpty()) return;
    Matcher m = PLACEHOLDER.matcher(text);
    while (m.find()) {
      String path = m.group(1);
      out.putIfAbsent(
          elementId + "|" + path,
          new BindingDto(elementId, "Text", m.group(0), path, source, "linked", path));
    }
  }
}
