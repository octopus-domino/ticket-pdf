package ru.ticket.journey;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.FontRenderContext;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import ru.ticket.dto.*;

/** Printable quote composition. Screen, preview and PDF consume exactly these pages. */
final class ProposalDocumentLayout {
  private static final Font REGULAR = font("DejaVuSans"), BOLD = font("DejaVuSans-Bold");
  private static final FontRenderContext METRICS = new FontRenderContext(null, true, true);
  private static final double LEFT = 56, WIDTH = 682, LIMIT = 980;
  private static final String INK = "#243347", MUTED = "#6b7888", RULE = "#e1e7ee";

  private record Palette(String accent, String wash) {}

  private record Sheet(Slide slide, double start) {}

  private final List<Slide> pages = new ArrayList<>();

  ExportRequest compose(JourneyService.View view) {
    var items = ProposalContent.read(view.data().getOrDefault("positions", "[]"));
    var palette =
        switch (view.data().getOrDefault("theme", "ink")) {
          case "sage" -> new Palette("#315c4e", "#f1f6f3");
          case "rose" -> new Palette("#965e4d", "#fcf4f0");
          default -> new Palette("#25466a", "#f1f5fa");
        };
    String currency = view.data().getOrDefault("currency", "RUB"),
        symbol =
            switch (currency) {
              case "RUB" -> "₽";
              case "EUR" -> "€";
              case "USD" -> "$";
              default -> currency;
            };
    DecimalFormat quantity = decimal(0, 4), price = decimal(2, 4), money = decimal(2, 2);
    Sheet sheet = page(view, palette, true, symbol);
    double y = sheet.start();
    for (int index = 0; index < items.size(); index++) {
      var item = items.get(index);
      var name = wrap(item.name().strip().replaceAll("\\s+", " "), REGULAR, 13, 288);
      double height = Math.max(44, name.size() * 19 + 22);
      if (y + height > LIMIT) {
        sheet = page(view, palette, true, symbol);
        y = sheet.start();
      }
      var page = sheet.slide();
      if (index % 2 == 1) page.elements.add(rect(LEFT, y, WIDTH, height, palette.wash()));
      page.elements.add(
          text(String.valueOf(index + 1), LEFT + 8, y + 14, 28, REGULAR, 10, MUTED, "left"));
      for (int line = 0; line < name.size(); line++)
        page.elements.add(
            text(name.get(line), 100, y + 12 + line * 19, 288, REGULAR, 13, INK, "left"));
      page.elements.add(
          number(quantity.format(new BigDecimal(item.quantity())), 400, y + 13, 76, 12, INK));
      page.elements.add(
          number(price.format(new BigDecimal(item.price())), 488, y + 13, 100, 12, INK));
      page.elements.add(number(money.format(item.total()), 600, y + 13, 130, 12, INK));
      page.elements.add(rule(y + height, RULE));
      y += height;
    }
    y += 24;
    if (y + 92 > LIMIT) {
      sheet = page(view, palette, false, symbol);
      y = sheet.start();
    }
    sheet
        .slide()
        .elements
        .add(text("Позиций: " + items.size(), LEFT, y + 14, 350, REGULAR, 12, MUTED, "left"));
    sheet.slide().elements.add(rect(450, y, 288, 84, palette.accent()));
    sheet.slide().elements.add(text("ИТОГО", 468, y + 13, 250, BOLD, 10, "#ffffff", "left"));
    sheet
        .slide()
        .elements
        .add(
            number(
                money.format(new BigDecimal(ProposalContent.total(items))) + " " + symbol,
                468,
                y + 35,
                250,
                26,
                "#ffffff"));
    y += 110;
    for (String key : List.of("description", "contact")) {
      String value = view.data().getOrDefault(key, "").strip();
      if (value.isEmpty()) continue;
      if (y + 64 > LIMIT) {
        sheet = page(view, palette, false, symbol);
        y = sheet.start();
      }
      sheet
          .slide()
          .elements
          .add(
              text(
                  key.equals("contact") ? "КОНТАКТЫ" : "УСЛОВИЯ ПРЕДЛОЖЕНИЯ",
                  LEFT,
                  y,
                  WIDTH,
                  BOLD,
                  10,
                  palette.accent(),
                  "left"));
      y += 26;
      for (String paragraph : value.split("\\R", -1)) {
        if (paragraph.isBlank()) {
          y += 10;
          continue;
        }
        for (String line : wrap(paragraph, REGULAR, 13, WIDTH - 4)) {
          if (y + 21 > LIMIT) {
            sheet = page(view, palette, false, symbol);
            y = sheet.start();
          }
          sheet.slide().elements.add(text(line, LEFT, y, WIDTH, REGULAR, 13, INK, "left"));
          y += 21;
        }
      }
      y += 22;
    }
    for (int index = 0; index < pages.size(); index++) {
      var page = pages.get(index);
      page.elements.add(rule(1046, RULE));
      page.elements.add(
          text(
              "CoDoc"
                  + (view.id().matches("[A-Za-z0-9]{12}") ? " / " + view.id() : " / Предпросмотр"),
              LEFT,
              1062,
              450,
              REGULAR,
              9,
              MUTED,
              "left"));
      page.elements.add(
          text(
              "Страница " + (index + 1) + " из " + pages.size(),
              558,
              1062,
              180,
              REGULAR,
              9,
              MUTED,
              "right"));
    }
    var request = new ExportRequest();
    request.slides = List.copyOf(pages);
    return request;
  }

  private Sheet page(JourneyService.View view, Palette palette, boolean table, String symbol) {
    if (pages.size() >= 100)
      throw ru.ticket.common.ApiException.badRequest("КП слишком большое. Разделите предложение.");
    var page = new Slide();
    page.id = view.id() + "-proposal-" + pages.size();
    page.name = view.title();
    String issuer = view.data().getOrDefault("issuer", "").strip();
    page.elements.add(rect(LEFT, 42, 36, 36, palette.accent()));
    String mark =
        issuer.isBlank()
            ? "КП"
            : issuer
                .substring(0, issuer.offsetByCodePoints(0, 1))
                .toUpperCase(Locale.forLanguageTag("ru"));
    page.elements.add(text(mark, LEFT + 4, 50, 28, BOLD, 18, "#ffffff", "center"));
    double y = 44;
    for (String line :
        wrap(issuer.isBlank() ? "Коммерческое предложение" : issuer, BOLD, 13, 620)) {
      page.elements.add(text(line, 108, y, 630, BOLD, 13, palette.accent(), "left"));
      y += 19;
    }
    y = Math.max(106, y + 24);
    int titleSize = pages.isEmpty() ? 27 : 18;
    for (String line : wrap(view.title(), BOLD, titleSize, WIDTH - 8)) {
      page.elements.add(text(line, LEFT, y, WIDTH, BOLD, titleSize, INK, "left"));
      y += titleSize * 1.35;
    }
    String recipient = view.data().getOrDefault("recipient", "").strip();
    if (!recipient.isBlank()) {
      y += 16;
      page.elements.add(text("ПОДГОТОВЛЕНО ДЛЯ", LEFT, y, WIDTH, BOLD, 9, MUTED, "left"));
      y += 19;
      for (String line : wrap(recipient, REGULAR, 14, WIDTH - 4)) {
        page.elements.add(text(line, LEFT, y, WIDTH, REGULAR, 14, INK, "left"));
        y += 20;
      }
    }
    y += 26;
    if (table) {
      page.elements.add(rect(LEFT, y, WIDTH, 36, palette.accent()));
      page.elements.add(text("№", LEFT + 8, y + 12, 28, BOLD, 10, "#ffffff", "left"));
      page.elements.add(text("Наименование", 100, y + 11, 288, BOLD, 11, "#ffffff", "left"));
      page.elements.add(text("Кол-во", 400, y + 11, 76, BOLD, 11, "#ffffff", "right"));
      page.elements.add(text("Цена, " + symbol, 488, y + 11, 100, BOLD, 11, "#ffffff", "right"));
      page.elements.add(text("Сумма, " + symbol, 600, y + 11, 130, BOLD, 11, "#ffffff", "right"));
      y += 36;
    }
    pages.add(page);
    return new Sheet(page, y);
  }

  private static TextElement number(
      String value, double x, double y, double width, int size, String color) {
    while (size > 8
        && REGULAR.deriveFont((float) size).getStringBounds(value, METRICS).getWidth() > width)
      size--;
    return text(value, x, y, width, REGULAR, size, color, "right");
  }

  private static TextElement text(
      String value,
      double x,
      double y,
      double width,
      Font face,
      int size,
      String color,
      String align) {
    var text = new TextElement();
    text.type = "text";
    text.id = UUID.randomUUID().toString();
    text.text = value;
    text.x = x;
    text.y = y;
    text.width = width;
    text.height = size * 1.4;
    text.fontFamily = "DejaVu Sans";
    text.fontSize = size;
    text.fontStyle = face == BOLD ? "bold" : "normal";
    text.fill = color;
    text.align = align;
    text.lineHeight = 1.4;
    return text;
  }

  private static ShapeElement rect(double x, double y, double width, double height, String color) {
    var shape = new ShapeElement();
    shape.type = "shape";
    shape.id = UUID.randomUUID().toString();
    shape.x = x;
    shape.y = y;
    shape.width = width;
    shape.height = height;
    shape.fill = color;
    shape.stroke = "transparent";
    shape.strokeWidth = 0;
    return shape;
  }

  private static LineElement rule(double y, String color) {
    var line = new LineElement();
    line.type = "line";
    line.id = UUID.randomUUID().toString();
    line.x = LEFT;
    line.y = y;
    line.width = WIDTH;
    line.points = new double[] {0, 0, WIDTH, 0};
    line.stroke = color;
    line.strokeWidth = .6;
    return line;
  }

  private static List<String> wrap(String value, Font face, int size, double width) {
    var result = new ArrayList<String>();
    var current = new StringBuilder();
    Font font = face.deriveFont((float) size);
    for (int cp : value.replace('\r', ' ').codePoints().toArray()) {
      if (cp == '\n') {
        result.add(current.toString());
        current.setLength(0);
        continue;
      }
      String next = new String(Character.toChars(cp));
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

  private static DecimalFormat decimal(int min, int max) {
    var symbols = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("ru"));
    symbols.setGroupingSeparator('\u00a0');
    var format = new DecimalFormat("#,##0", symbols);
    format.setMinimumFractionDigits(min);
    format.setMaximumFractionDigits(max);
    return format;
  }

  private static Font font(String name) {
    try (var input = ProposalDocumentLayout.class.getResourceAsStream("/fonts/" + name + ".ttf")) {
      if (input == null) throw new IllegalStateException("Missing quote font");
      var font = Font.createFont(Font.TRUETYPE_FONT, input);
      GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
      return font;
    } catch (Exception error) {
      throw new IllegalStateException("Cannot load quote font", error);
    }
  }
}
