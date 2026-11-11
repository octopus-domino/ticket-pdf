package ru.ticket.journey;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.FontRenderContext;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import ru.ticket.dto.*;

/** One paginated event composition, shared by the browser artwork and PDF export. */
final class EventDocumentLayout {
  private static final FontRenderContext METRICS = new FontRenderContext(null, true, true);
  private static final Font SANS = font("DejaVuSans"),
      BOLD = font("DejaVuSans-Bold"),
      SERIF = font("DejaVuSerif"),
      ITALIC = font("DejaVuSerif-Italic");
  private static final double WIDTH = 666, LEFT = 64, BOTTOM = 850;

  private record Palette(String paper, String ink, String muted, String accent, String rule) {}

  private record Copy(String value, Font font, int size, double gap) {}

  ExportRequest compose(
      JourneyService.View view, String url, JourneySiteService.Definition definition) {
    var palette =
        switch (view.data()
            .getOrDefault("theme", definition == null ? "rose" : definition.theme())) {
          case "ink" -> new Palette("#172e35", "#faf3e5", "#c0cdca", "#dbbc87", "#456169");
          case "sage" -> new Palette("#f5f5ed", "#233e35", "#63766b", "#647e60", "#ccd5c7");
          default -> new Palette("#fcf4ed", "#50362f", "#826d64", "#a5684f", "#e1ccc0");
        };
    var copies = new ArrayList<Copy>();
    var data = view.data();
    if (data.getOrDefault("recipient", "").length() > 200)
      copies.add(new Copy(data.get("recipient"), ITALIC, 25, 20));
    String date = humanDate(data.getOrDefault("date", "")),
        time = data.getOrDefault("time", "").strip();
    if (!date.isBlank() || !time.isBlank())
      copies.add(
          new Copy(date + (date.isBlank() || time.isBlank() ? "" : "  ·  ") + time, BOLD, 23, 14));
    if (!data.getOrDefault("location", "").isBlank())
      copies.add(new Copy(data.get("location"), SANS, 19, 30));
    if (!data.getOrDefault("description", "").isBlank())
      copies.add(new Copy(data.get("description"), SANS, 19, 26));
    var standard =
        Set.of(
            "theme",
            "site",
            "recipient",
            "issuer",
            "date",
            "time",
            "location",
            "description",
            "contact",
            "valid_until",
            "timezone");
    if (definition != null)
      for (var field : definition.fields()) {
        if (!standard.contains(field.name()) && !data.getOrDefault(field.name(), "").isBlank())
          copies.add(new Copy(field.label() + ": " + data.get(field.name()), SANS, 18, 18));
      }
    if (!data.getOrDefault("issuer", "").isBlank())
      copies.add(new Copy(data.get("issuer"), ITALIC, 21, 18));
    if (!data.getOrDefault("contact", "").isBlank())
      copies.add(new Copy(data.get("contact"), SANS, 17, 16));
    String until = data.getOrDefault("valid_until", "");
    if (!until.isBlank() && !until.equals(data.getOrDefault("date", "")))
      copies.add(new Copy("Действует по " + humanDate(until), SANS, 15, 16));

    var pages = new ArrayList<Slide>();
    Slide page = page(view, palette, url, pages, false);
    double y = firstHeading(page, view, palette);
    for (var copy : copies) {
      for (String paragraph : copy.value().strip().split("\\R", -1)) {
        if (paragraph.isBlank()) {
          y += 12;
          continue;
        }
        for (String line : wrap(paragraph, copy.font(), copy.size(), WIDTH - 12)) {
          double advance = copy.size() * 1.48;
          if (y + advance > BOTTOM) {
            page = page(view, palette, url, pages, true);
            y = continuationHeading(page, view, palette);
          }
          page.elements.add(text(line, LEFT, y, WIDTH, copy.font(), copy.size(), palette.ink()));
          y += advance;
        }
      }
      y += copy.gap();
    }
    var result = new ExportRequest();
    result.slides = pages;
    return result;
  }

  private double firstHeading(Slide page, JourneyService.View view, Palette p) {
    int size = 52;
    List<String> lines = wrap(view.title(), SERIF, size, WIDTH - 8);
    while (lines.size() > 3 && size > 34) {
      size -= 2;
      lines = wrap(view.title(), SERIF, size, WIDTH - 8);
    }
    double y = 174;
    for (String line : lines) {
      page.elements.add(text(line, LEFT, y, WIDTH, SERIF, size, p.ink()));
      y += size * 1.25;
    }
    String recipient = view.data().getOrDefault("recipient", "").strip();
    if (!recipient.isBlank() && recipient.length() <= 200) {
      y += 18;
      int recipientSize = 29;
      while (wrap(recipient, ITALIC, recipientSize, WIDTH - 8).size() > 3 && recipientSize > 18)
        recipientSize--;
      for (String line : wrap(recipient, ITALIC, recipientSize, WIDTH - 8)) {
        page.elements.add(text(line, LEFT, y, WIDTH, ITALIC, recipientSize, p.accent()));
        y += recipientSize * 1.4;
      }
    }
    y += 25;
    page.elements.add(rule(y, p.rule()));
    return y + 32;
  }

  private double continuationHeading(Slide page, JourneyService.View view, Palette p) {
    double y = 126;
    for (String line : wrap(view.title(), SERIF, 27, WIDTH - 8)) {
      page.elements.add(text(line, LEFT, y, WIDTH, SERIF, 27, p.ink()));
      y += 36;
    }
    page.elements.add(rule(y + 10, p.rule()));
    return y + 40;
  }

  private Slide page(
      JourneyService.View view, Palette p, String url, List<Slide> pages, boolean continued) {
    if (pages.size() >= 40)
      throw ru.ticket.common.ApiException.badRequest(
          "Текст слишком длинный. Сократите приглашение.");
    var page = new Slide();
    page.id = view.id() + "-event-" + pages.size();
    page.name = view.title();
    page.background = p.paper();
    page.elements.add(
        text(
            view.kind() == JourneyService.Kind.TICKET ? "ВАШ БИЛЕТ" : "ПРИГЛАШЕНИЕ",
            LEFT,
            64,
            500,
            BOLD,
            12,
            p.muted()));
    if (!continued) {
      var branch = new PathElement();
      branch.id = "event-branch";
      branch.type = "path";
      branch.width = 794;
      branch.height = 1123;
      branch.fill = "none";
      branch.stroke = p.accent();
      branch.strokeWidth = 1.3;
      branch.d =
          "M 584 144 C 620 124 655 99 697 47 M 622 119 C 595 118 585 104 583 88 C 606 89 621 101"
              + " 622 119 M 643 103 C 642 80 653 64 671 58 C 679 79 665 96 643 103 M 665 80 C 688"
              + " 86 705 78 714 63 C 692 56 676 64 665 80";
      page.elements.add(branch);
    }
    page.elements.add(rule(916, p.rule()));
    boolean published = view.id().matches("[A-Za-z0-9]{12}");
    page.elements.add(text("CoDoc", LEFT, 958, 400, SERIF, 22, p.ink()));
    page.elements.add(
        text(
            published ? view.id() + "  /  " + (pages.size() + 1) : "Предпросмотр",
            LEFT,
            997,
            450,
            SANS,
            12,
            p.muted()));
    page.elements.add(
        text(
            published ? "Страница события и детали — по QR" : "QR появится после публикации",
            LEFT,
            1023,
            450,
            SANS,
            12,
            p.muted()));
    if (published) {
      var qr = new QrElement();
      qr.type = "qr";
      qr.id = "event-qr";
      qr.x = 612;
      qr.y = 946;
      qr.width = 112;
      qr.height = 112;
      qr.text = url;
      page.elements.add(qr);
    }
    pages.add(page);
    return page;
  }

  private static LineElement rule(double y, String color) {
    var line = new LineElement();
    line.id = "rule-" + y;
    line.type = "line";
    line.x = LEFT;
    line.y = y;
    line.width = WIDTH;
    line.points = new double[] {0, 0, WIDTH, 0};
    line.stroke = color;
    line.strokeWidth = 1;
    return line;
  }

  private static TextElement text(
      String value, double x, double y, double width, Font font, int size, String color) {
    var result = new TextElement();
    result.id = UUID.randomUUID().toString();
    result.type = "text";
    result.x = x;
    result.y = y;
    result.width = width;
    result.height = size * 1.48;
    result.text = value;
    result.fontFamily = font.getFamily(Locale.ROOT);
    result.fontSize = size;
    result.fill = color;
    result.lineHeight = 1.48;
    String name = font.getFontName(Locale.ROOT).toLowerCase(Locale.ROOT);
    result.fontStyle =
        (name.contains("bold") ? "bold " : "") + (name.contains("italic") ? "italic" : "normal");
    return result;
  }

  private static List<String> wrap(String value, Font face, int size, double width) {
    Font font = face.deriveFont((float) size);
    var result = new ArrayList<String>();
    StringBuilder current = new StringBuilder();
    for (int cp : value.replace('\r', ' ').codePoints().toArray()) {
      String next = new String(Character.toChars(cp));
      if (cp == '\n') {
        result.add(current.toString());
        current.setLength(0);
        continue;
      }
      if (current.length() > 0
          && font.getStringBounds(current + next, METRICS).getWidth() > width) {
        int last = current.lastIndexOf(" ");
        if (last > 0) {
          result.add(current.substring(0, last));
          current.delete(0, last + 1);
        } else {
          result.add(current.toString());
          current.setLength(0);
        }
      }
      current.append(next);
    }
    result.add(current.toString());
    return result;
  }

  private static String humanDate(String raw) {
    if (raw.isBlank()) return "";
    try {
      return LocalDate.parse(raw)
          .format(DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.forLanguageTag("ru")));
    } catch (java.time.DateTimeException ignored) {
      return raw;
    }
  }

  private static Font font(String name) {
    try (var input = EventDocumentLayout.class.getResourceAsStream("/fonts/" + name + ".ttf")) {
      if (input == null) throw new IllegalStateException("Missing event font: " + name);
      var font = Font.createFont(Font.TRUETYPE_FONT, input);
      GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
      return font;
    } catch (Exception error) {
      throw new IllegalStateException("Cannot load event font", error);
    }
  }
}
