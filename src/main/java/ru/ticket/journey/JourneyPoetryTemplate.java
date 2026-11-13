package ru.ticket.journey;

import java.time.*;
import java.time.format.*;
import java.util.*;
import ru.ticket.dto.*;
import ru.ticket.journey.poetry.PoetryCardsService;
import ru.ticket.generation.application.port.GenerationCatalog;
import ru.ticket.schema.domain.FieldDef;
import ru.ticket.template.domain.BindingDto;

/** One editable postcard page, repeated over seven selected source poems by the common constructor. */
final class JourneyPoetryTemplate {
  private JourneyPoetryTemplate() {}
  static JourneyConstructorSource.Packet source(JourneyService.View view, PoetryCardsService.Bundle bundle, String publicUrl) {
    var page = new Slide(); page.id = "poetry-card"; page.name = view.title(); page.width = 559; page.height = 794; page.format = "a5";
    page.background = "#fbf8f0"; page.repeat = new Slide.Repeat("cards", 1);
    var border = new ShapeElement(); border.type = "shape"; border.id = "poem-border"; border.shape = "rect";
    border.x = 20; border.y = 20; border.width = 519; border.height = 754; border.fill = "transparent"; border.stroke = "#b4c5af"; border.strokeWidth = 1.5; page.elements.add(border);
    page.elements.add(text("card-date", "{{cards[0].date}}", 42, 40, 475, 32, 14, "{{cards[0].font}}"));
    page.elements.add(text("poem-title", "{{cards[0].title}}", 42, 90, 475, 70, 26, "{{cards[0].font}}"));
    page.elements.add(text("poem-body", "{{cards[0].poem}}", 42, 176, 475, 462, 25, "{{cards[0].font}}"));
    page.elements.add(text("poem-author", "{{cards[0].author}}", 42, 652, 475, 45, 20, "{{cards[0].font}}"));
    page.elements.add(text("poem-source", "{{cards[0].sourceUrl}}", 42, 706, 402, 46, 9, "DejaVu Sans"));
    var qr = new QrElement(); qr.type = "qr"; qr.id = "poetry-publication-qr"; qr.text = "{{fields.publication_url}}"; qr.x = 461; qr.y = 706; qr.width = 56; qr.height = 56; page.elements.add(qr);
    var cards = new ArrayList<Map<String,Object>>();
    if (bundle != null) for (var card : bundle.cards()) {
      String date = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.forLanguageTag(bundle.language())).format(LocalDate.parse(card.date()));
      var row = new LinkedHashMap<String,Object>(); row.put("date", date); row.put("title", card.title()); row.put("poem", ru.ticket.journey.poetry.PoetryDisplayText.of(card));
      row.put("author", card.author()); row.put("sourceUrl", card.sourceUrl()); row.put("font", JourneyAlbumTemplate.handwriting(card.poem() + card.author())); cards.add(row);
    }
    var fields = new ArrayList<FieldDef>(); fields.add(new FieldDef("cards", "array", false, "Cards", null));
    fields.add(new FieldDef("fields.publication_url", "string", false, "Publication link", null));
    for (var name : List.of("date", "title", "poem", "author", "sourceUrl", "font")) fields.add(new FieldDef("cards." + name, "string", false, name, null));
    var bindings = new ArrayList<BindingDto>();
    for (var element : page.elements) {
      String value = element instanceof TextElement text ? text.text + " " + text.fontFamily : element instanceof QrElement code ? code.text : "";
      var matcher = java.util.regex.Pattern.compile("\\{\\{([^}]+)}}").matcher(value);
      while (matcher.find()) { String path = matcher.group(1); var binding = new BindingDto(element.id, path, matcher.group(), path, "auto", "linked", path); binding.expectedType = "string"; bindings.add(binding); }
    }
    var model = Map.<String,Object>of("cards", cards, "fields", Map.of("publication_url", publicUrl));
    return new JourneyConstructorSource.Packet(new GenerationCatalog.Template(List.of(page), null, fields, bindings), model, new ExportOptions());
  }
  private static TextElement text(String id, String value, double x, double y, double width, double height, double size, String font) {
    var text = new TextElement(); text.type = "text"; text.id = id; text.text = value; text.x = x; text.y = y;
    text.width = width; text.height = height; text.fontSize = size; text.fontFamily = font; text.fill = "#3e4c41"; text.lineHeight = 1.25;
    text.autoFit = true; text.minFontSize = Math.min(10, size); return text;
  }
}
