package ru.ticket.journey;

import java.util.*;
import ru.ticket.dto.*;
import ru.ticket.generation.application.port.GenerationCatalog;
import ru.ticket.schema.domain.FieldDef;

/** Four editable photo/caption slots, repeated by the common constructor over the photos model. */
final class JourneyAlbumTemplate {
  private JourneyAlbumTemplate() {}
  static JourneyConstructorSource.Packet source(JourneyService.View view, List<Map<String,Object>> photos, String publicUrl) {
    var slide = new Slide(); slide.id = "album-page"; slide.name = view.title(); slide.background = "#fbf8f0";
    slide.repeat = new Slide.Repeat("photos", 4);
    var values = new LinkedHashMap<String, Object>(); values.put("title", view.title());
    values.put("description", view.data().getOrDefault("description", ""));
    values.put("publication_url", publicUrl);
    var model = Map.<String, Object>of("fields", values, "photos", photos);
    String handwriting = handwriting(view.title() + view.data().getOrDefault("description", ""));
    slide.elements.add(text("album-title", "{{fields.title}}", 50, 36, 694, 64, 32, handwriting));
    slide.elements.add(text("album-description", "{{fields.description}}", 50, 104, 694, 56, 16, handwriting));
    for (int index = 0; index < 4; index++) {
      double x = 50 + index % 2 * 358, y = 176 + index / 2 * 430;
      var frame = new ShapeElement(); frame.type = "shape"; frame.id = "photo-frame-" + index; frame.shape = "rect";
      frame.x = x - 7; frame.y = y - 7; frame.width = 343; frame.height = 402; frame.fill = "#ffffff"; frame.stroke = "#e5ded0"; frame.strokeWidth = 1;
      slide.elements.add(frame);
      var image = new ImageElement(); image.type = "image"; image.id = "photo-" + index;
      image.x = x; image.y = y; image.width = 329; image.height = 280; image.src = "{{photos[" + index + "].image}}";
      slide.elements.add(image);
      slide.elements.add(text("caption-" + index, "{{photos[" + index + "].caption}}", x + 6, y + 288, 317, 82, 21, "{{photos[" + index + "].font}}"));
      slide.elements.add(text("contributor-" + index, "{{photos[" + index + "].contributor}}", x + 6, y + 370, 317, 23, 13, "{{photos[" + index + "].font}}"));
    }
    var qr = new QrElement(); qr.type = "qr"; qr.id = "album-qr"; qr.x = 688; qr.y = 1032; qr.width = 56; qr.height = 56; qr.text = "{{fields.publication_url}}"; slide.elements.add(qr);
    var fields = List.of(new FieldDef("fields.title", "string", true, "Title", null), new FieldDef("fields.publication_url", "string", false, "Link", null),
        new FieldDef("fields.description", "string", false, "Description", null), new FieldDef("photos", "array", false, "Photos", null),
        new FieldDef("photos.image", "image", false, "Photo", null), new FieldDef("photos.caption", "string", false, "Caption", null),
        new FieldDef("photos.contributor", "string", false, "Contributor", null), new FieldDef("photos.font", "string", false, "Font", null));
    var bindings = new ArrayList<ru.ticket.template.domain.BindingDto>();
    for (var element : slide.elements) {
      String value = element instanceof TextElement text ? text.text + " " + text.fontFamily : element instanceof ImageElement image ? image.src : element instanceof QrElement code ? code.text : "";
      var matcher = java.util.regex.Pattern.compile("\\{\\{([^}]+)}}").matcher(value);
      while (matcher.find()) {
        String path = matcher.group(1);
        var binding = new ru.ticket.template.domain.BindingDto(element.id, path, matcher.group(), path, "auto", "linked", path);
        binding.expectedType = element instanceof ImageElement ? "image" : "string"; bindings.add(binding);
      }
    }
    return new JourneyConstructorSource.Packet(new GenerationCatalog.Template(List.of(slide), null, fields, bindings), model, new ExportOptions());
  }
  static String handwriting(String value) {
    return value.codePoints().anyMatch(character -> Character.UnicodeScript.of(character) == Character.UnicodeScript.HAN) ? "Zhi Mang Xing" : "Marck Script";
  }
  private static TextElement text(String id, String value, double x, double y, double width, double height, double size, String font) {
    var text = new TextElement(); text.id = id; text.type = "text"; text.text = value; text.x = x; text.y = y;
    text.width = width; text.height = height; text.fontSize = size; text.fontFamily = font; text.fill = "#3e4c41"; text.lineHeight = 1.2;
    text.autoFit = true; text.minFontSize = 8.0; return text;
  }
}
