package ru.ticket.journey;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.*;
import ru.ticket.common.ApiException;
import ru.ticket.dto.TextElement;

final class JourneyTypography {
  private static final FontRenderContext METRICS = new FontRenderContext(null, true, true);
  private static final Font REGULAR = font("DejaVuSans"), BOLD = font("DejaVuSans-Bold");

  static TextElement fit(
      String content,
      double x,
      double y,
      double width,
      double height,
      int size,
      boolean bold,
      String color) {
    Font font = bold ? BOLD : REGULAR;
    var lines = wrap(content, font, size, width);
    while (size > 8 && lines.size() * size * 1.3 > height)
      lines = wrap(content, font, --size, width);
    if (lines.size() * size * 1.3 > height) throw ApiException.badRequest("journey.print.too_long");
    var text = new TextElement();
    text.id = UUID.randomUUID().toString();
    text.type = "text";
    text.x = x;
    text.y = y;
    text.width = width;
    text.height = height;
    text.fontSize = size;
    text.fontFamily = "DejaVu Sans";
    text.fontStyle = bold ? "bold" : "normal";
    text.fill = color;
    text.lineHeight = 1.3;
    text.text = String.join("\n", lines);
    return text;
  }

  private static List<String> wrap(String content, Font font, int size, double width) {
    var lines = new ArrayList<String>();
    StringBuilder line = new StringBuilder();
    for (int cp : content.replace("\r", "").codePoints().toArray()) {
      if (cp == '\n') {
        lines.add(line.toString());
        line.setLength(0);
        continue;
      }
      String next = new String(Character.toChars(cp));
      if (line.length() > 0
          && font.deriveFont((float) size).getStringBounds(line + next, METRICS).getWidth()
              > width - 2) {
        int split = line.lastIndexOf(" ");
        if (split > 0) {
          lines.add(line.substring(0, split));
          line.delete(0, split + 1);
        } else {
          lines.add(line.toString());
          line.setLength(0);
        }
      }
      line.append(next);
    }
    lines.add(line.toString());
    return lines;
  }

  private static Font font(String name) {
    try (var stream = JourneyTypography.class.getResourceAsStream("/fonts/" + name + ".ttf")) {
      return Font.createFont(Font.TRUETYPE_FONT, stream);
    } catch (Exception error) {
      throw new IllegalStateException("Bundled print font missing", error);
    }
  }
}
