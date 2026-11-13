package ru.ticket.generation.domain;

import ru.ticket.dto.TextElement;
import ru.ticket.dto.Padding;
import ru.ticket.text.AwtTextLayoutService;

/** Opt-in template behaviour: fit model text into its original box without changing page geometry. */
final class TextAutoFit {
  private static final AwtTextLayoutService METRICS = new AwtTextLayoutService();
  private TextAutoFit() {}
  static void apply(TextElement text) {
    if (!Boolean.TRUE.equals(text.autoFit) || text.html != null && !text.html.isBlank()) return;
    var padding = text.padding == null ? new Padding() : text.padding;
    double width = Math.max(10, text.width - padding.left - padding.right), height = Math.max(10, text.height - padding.top - padding.bottom);
    double minimum = text.minFontSize == null ? 8 : text.minFontSize;
    if (!Double.isFinite(minimum) || minimum < 6 || minimum > text.fontSize) throw new IllegalArgumentException("Invalid minimum font size");
    double size = text.fontSize;
    while (METRICS.wrap(text.text, width, text.fontFamily, (int)Math.round(size), text.fontStyle).size()
        * size * (text.lineHeight > 0 ? text.lineHeight : 1.5) > height) {
      if (size <= minimum) throw new IllegalArgumentException("Text does not fit its template box");
      size = Math.max(minimum, size - 1);
    }
    text.fontSize = size;
  }
}
