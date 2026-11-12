package ru.ticket.journey;

import java.util.*;
import ru.ticket.common.ApiException;
import ru.ticket.dto.*;

/** Single cards and A4 grid printing reuse the same physical-sized composition. */
final class JourneyGridLayout {
  static boolean supports(JourneyService.Kind kind) {
    return Set.of(
            JourneyService.Kind.VCARD, JourneyService.Kind.LABEL, JourneyService.Kind.PRICETAG)
        .contains(kind);
  }

  static double dimension(Map<String, String> data, String key, double fallback, double max) {
    try {
      double mm = Double.parseDouble(data.getOrDefault(key, String.valueOf(fallback)));
      if (!Double.isFinite(mm) || mm < 30 || mm > max) throw new NumberFormatException();
      return mm * 96 / 25.4;
    } catch (NumberFormatException error) {
      throw ApiException.badRequest("journey.print.size");
    }
  }

  ExportRequest single(JourneyService.View view, String url) {
    var data = view.data();
    boolean card = view.kind() == JourneyService.Kind.VCARD,
        price = view.kind() == JourneyService.Kind.PRICETAG;
    var page = new Slide();
    page.width = dimension(data, "print_width", card ? 90 : 70, 190);
    page.height =
        dimension(data, "print_height", view.kind() == JourneyService.Kind.LABEL ? 40 : 50, 277);
    page.id = view.id();
    page.name = view.title();
    page.background = "#ffffff";
    String ink = "#243347", muted = "#566777";
    var border = new ShapeElement();
    border.id = "border";
    border.type = "shape";
    border.shape = "rect";
    border.x = 1;
    border.y = 1;
    border.width = page.width - 2;
    border.height = page.height - 2;
    border.fill = "transparent";
    border.stroke = "#d0d9df";
    border.strokeWidth = .7;
    page.elements.add(border);
    double qrSize = Math.min(70, Math.min(page.width * .27, page.height * .45));
    double textWidth = page.width - qrSize - 34;
    page.elements.add(
        JourneyTypography.fit(
            data.getOrDefault("recipient", view.title()),
            12,
            12,
            page.width - 24,
            38,
            card ? 20 : 16,
            true,
            ink));
    if (price) {
      String amount = JourneyMoney.formatted(data);
      page.elements.add(JourneyTypography.fit(amount, 12, 54, textWidth, 42, 28, true, ink));
    }
    var captions = JourneyFieldLabels.read(data.get("field_labels"));
    var body = new ArrayList<String>();
    for (String key :
        card
            ? List.of("issuer", "role", "phone", "email", "website")
            : price
                ? List.of("issuer", "description")
                : List.of("batch", "weight", "date", "valid_until", "issuer")) {
      String value = data.getOrDefault(key, "");
      if (!value.isBlank()) body.add((card ? "" : captions.getOrDefault(key, key) + ": ") + value);
    }
    double top = price ? 100 : 56;
    page.elements.add(
        JourneyTypography.fit(
            String.join("\n", body),
            12,
            top,
            textWidth,
            page.height - top - 12,
            card ? 12 : 10,
            false,
            muted));
    var qr = new QrElement();
    qr.id = "qr";
    qr.type = "qr";
    qr.x = page.width - qrSize - 12;
    qr.y = page.height - qrSize - 12;
    qr.width = qrSize;
    qr.height = qrSize;
    qr.text = url;
    page.elements.add(qr);
    var result = new ExportRequest();
    result.slides = List.of(page);
    return result;
  }

  ExportRequest batch(
      List<JourneyService.View> views,
      java.util.function.Function<JourneyService.View, ExportRequest> compose) {
    var pages = new ArrayList<Slide>();
    Slide sheet = null;
    double x = 38, y = 38, rowHeight = 0;
    for (var view : views) {
      var cell = compose.apply(view).slides.get(0);
      if (sheet == null || y + cell.height > 1085) {
        sheet = new Slide();
        sheet.id = "print-" + pages.size();
        pages.add(sheet);
        x = 38;
        y = 38;
        rowHeight = 0;
      }
      if (x + cell.width > 756) {
        x = 38;
        y += rowHeight + 8;
        rowHeight = 0;
        if (y + cell.height > 1085) {
          sheet = new Slide();
          sheet.id = "print-" + pages.size();
          pages.add(sheet);
          y = 38;
        }
      }
      for (var element : cell.elements) {
        element.x += x;
        element.y += y;
        sheet.elements.add(element);
      }
      x += cell.width + 8;
      rowHeight = Math.max(rowHeight, cell.height);
      if (pages.size() > 200) throw ApiException.badRequest("Release exceeds 200 pages");
    }
    var result = new ExportRequest();
    result.slides = pages;
    return result;
  }
}
