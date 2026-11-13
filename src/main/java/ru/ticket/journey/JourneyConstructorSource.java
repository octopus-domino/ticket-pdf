package ru.ticket.journey;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.jsoup.parser.Parser;
import ru.ticket.dto.*;
import ru.ticket.generation.application.port.GenerationCatalog;
import ru.ticket.generation.domain.TemplateMaterializer;
import ru.ticket.schema.domain.FieldDef;
import ru.ticket.template.domain.BindingDto;

/**
 * Transitional native-template projection. Preserves every page and object while moving values
 * through the constructor's real schema/binding/materialization contract. Layout recipes still
 * need migration to persistent editable sources; this adapter alone does not finish that work.
 */
public final class JourneyConstructorSource {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  public record Packet(GenerationCatalog.Template template, Map<String, Object> model, ExportOptions options) {
    public ExportRequest materialize() {
      var result = new ExportRequest(); result.options = options;
      result.slides = TemplateMaterializer.fill(template, model); return result;
    }
  }
  private final Map<String, Object> model = new LinkedHashMap<>();
  private final List<Map<String, String>> content = new ArrayList<>();
  private final List<FieldDef> fields = new ArrayList<>();
  private final List<BindingDto> bindings = new ArrayList<>();
  private int counter;
  private final Map<String, String> semantic;
  private JourneyConstructorSource(Map<String, String> source) {
    semantic = new LinkedHashMap<>();
    source.forEach((key, value) -> { if (key.matches("[A-Za-z_][A-Za-z0-9_]*") && !Set.of("__proto__", "prototype", "constructor").contains(key)) semantic.put(key, value); });
    model.put("fields", semantic); model.put("content", content);
    semantic.forEach((key, value) -> fields.add(new FieldDef("fields." + key, "string", false, key, null)));
    fields.add(new FieldDef("content", "array", false, "Content", null));
    fields.add(new FieldDef("content.text", "string", false, "Text", null));
    fields.add(new FieldDef("content.image", "image", false, "Image", null));
  }
  public static Packet project(ExportRequest prepared, Map<String, String> source) {
    var builder = new JourneyConstructorSource(source);
    var copy = MAPPER.convertValue(MAPPER.valueToTree(prepared), ExportRequest.class);
    for (var slide : copy.slides) {
      if (slide.backgroundImage != null) slide.backgroundImage = builder.slot(slide.id, "Background", slide.backgroundImage, "image");
      for (var element : slide.elements) builder.project(element);
    }
    return new Packet(new GenerationCatalog.Template(copy.slides, null, List.copyOf(builder.fields), List.copyOf(builder.bindings)),
        Collections.unmodifiableMap(builder.model), copy.options);
  }
  private String slot(String id, String label, String value, String type) {
    if (value == null || value.isEmpty()) return value;
    var direct = semantic.entrySet().stream().filter(entry -> value.equals(entry.getValue())).toList();
    if (type.equals("string") && direct.size() == 1) {
      String path = "fields." + direct.get(0).getKey(), token = "{{" + path + "}}";
      var binding = new BindingDto(id, direct.get(0).getKey(), token, path, "auto", "linked", path);
      binding.expectedType = type; bindings.add(binding); return token;
    }
    // IDs are generated for each publication. Keep a reused footer linked to the new record too.
    String publicationId = semantic.get("publication_id");
    if (type.equals("string") && publicationId != null && publicationId.matches("[A-Za-z0-9]{12}") && value.contains(publicationId)) {
      String token = "{{fields.publication_id}}";
      var binding = new BindingDto(id, "Publication ID", token, "fields.publication_id", "auto", "linked", "fields.publication_id");
      binding.expectedType = type; bindings.add(binding); return value.replace(publicationId, token);
    }
    String column = type.equals("image") ? "image" : "text";
    String path = "content[" + counter++ + "]." + column, token = "{{" + path + "}}";
    content.add(Map.of(column, value));
    var binding = new BindingDto(id, label, token, path, "auto", "linked", path);
    binding.expectedType = type; bindings.add(binding);
    return token;
  }
  private String html(String id, String value) {
    if (value == null || value.isBlank()) return value;
    var matcher = java.util.regex.Pattern.compile("(^|>)([^<]+)(?=<|$)").matcher(value);
    var result = new StringBuffer();
    while (matcher.find()) matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(matcher.group(1) +
        slot(id, "Rich text", Parser.unescapeEntities(matcher.group(2), false), "string")));
    matcher.appendTail(result); return result.toString();
  }
  private void project(Element element) {
    if (element instanceof TextElement text) {
      text.text = slot(text.id, "Text", text.text, "string"); text.html = html(text.id, text.html);
    } else if (element instanceof ImageElement image) image.src = slot(image.id, "Image", image.src, "image");
    else if (element instanceof QrElement qr) qr.text = slot(qr.id, "QR", qr.text, "string");
    else if (element instanceof ShapeElement shape) shape.text = slot(shape.id, "Shape text", shape.text, "string");
    else if (element instanceof FooterElement footer) {
      footer.text = slot(footer.id, "Footer", footer.text, "string"); footer.html = html(footer.id, footer.html);
    } else if (element instanceof TableElement table && table.data != null) {
      for (var row : table.data) for (int i = 0; i < row.size(); i++) row.set(i, slot(table.id, "Table cell", row.get(i), "string"));
    }
  }
}
