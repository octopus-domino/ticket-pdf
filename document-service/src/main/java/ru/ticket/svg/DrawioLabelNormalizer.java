package ru.ticket.svg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import ru.ticket.dto.Padding;
import ru.ticket.dto.TextElement;
import ru.ticket.text.AwtTextLayoutService;
import ru.ticket.text.richtext.RichTextLayout;
import ru.ticket.text.richtext.RichTextParser;
import ru.ticket.text.richtext.RichTextSvgRenderer;
import ru.ticket.text.richtext.TableLayoutService;
import ru.ticket.text.richtext.TableSvgRenderer;
import ru.ticket.util.XmlEscape;

/** Convert draw.io HTML labels to SVG text before Batik, which cannot paint foreignObject. */
final class DrawioLabelNormalizer {
  private static final String SVG_NAMESPACE = "http://www.w3.org/2000/svg";
  private static final AwtTextLayoutService MEASURE = new AwtTextLayoutService();
  private static final RichTextParser PARSER = new RichTextParser();
  private static final TableLayoutService TABLES = new TableLayoutService(MEASURE);
  private static final RichTextSvgRenderer RENDERER = new RichTextSvgRenderer(
      PARSER, MEASURE, TABLES, new TableSvgRenderer(TABLES, MEASURE));

  private DrawioLabelNormalizer() {}

  static String normalize(String svg) {
    var document = Jsoup.parse(svg, "", Parser.xmlParser());
    document.outputSettings().prettyPrint(false);
    var root = document.selectFirst("svg");
    if (root == null) return svg;

    double pageWidth = numeric(root.attr("width"), 1000);
    double pageHeight = numeric(root.attr("height"), 1000);
    for (var foreignObject : new ArrayList<>(document.select("foreignObject"))) {
      normalizeLabel(foreignObject, pageWidth, pageHeight);
    }
    return document.outerHtml();
  }

  private static void normalizeLabel(Element foreignObject, double pageWidth, double pageHeight) {
    Element container = foreignObject.children().first();
    if (container == null) {
      foreignObject.remove();
      return;
    }

    Map<String, String> box = style(container.attr("style"));
    Element content = findLabelContent(container);
    Map<String, String> inherited = inheritedStyle(content, foreignObject);
    double width = numeric(box.get("width"), numeric(foreignObject.attr("width"), pageWidth));
    if (width < 2) width = pageWidth;
    double height = numeric(box.get("height"), numeric(foreignObject.attr("height"), pageHeight));
    double x = numeric(foreignObject.attr("x"), 0)
        + numeric(box.get("margin-left"), 0) + numeric(box.get("padding-left"), 0);
    double y = numeric(foreignObject.attr("y"), 0)
        + numeric(box.get("margin-top"), 0) + numeric(box.get("padding-top"), 0);

    TextElement text = createText(content, inherited, box, width, height);
    double textHeight = RichTextLayout.layout(
        PARSER.parse(text.html), width, (int) Math.round(text.fontSize), text.fontFamily,
        text.lineHeight, MEASURE, TABLES).totalHeight();

    // draw.io centers labels in a flex box that may be only one pixel high.
    // Use measured text height to reproduce its overflow-based vertical positioning.
    String verticalAlignment = box.getOrDefault("align-items", "");
    if (verticalAlignment.contains("center")) {
      y += (height - textHeight) / 2;
      text.height = Math.max(text.height, textHeight);
    } else if (verticalAlignment.contains("end")) {
      y += height - textHeight;
      text.height = Math.max(text.height, textHeight);
    }

    StringBuilder output = new StringBuilder();
    RENDERER.render(output, text);
    Element fragment = Jsoup.parse(
        "<svg xmlns=\"" + SVG_NAMESPACE + "\"><g transform=\"translate(" + x + "," + y
            + ")\">" + output + "</g></svg>", "", Parser.xmlParser()).selectFirst("g");
    if (fragment == null) return;

    // Replacing the complete switch also removes draw.io's fallback warning text.
    if (foreignObject.parent() != null
        && foreignObject.parent().tagName().equalsIgnoreCase("switch")) {
      foreignObject.parent().replaceWith(fragment);
    } else {
      foreignObject.replaceWith(fragment);
    }
  }

  private static Element findLabelContent(Element container) {
    // Stop at the first actual label wrapper; picking a nested font span loses its siblings.
    for (var candidate : container.getAllElements()) {
      var properties = style(candidate.attr("style"));
      if (properties.containsKey("font-size")
          && numeric(properties.get("font-size"), 0) > 0
          && !candidate.text().isBlank()) {
        return candidate;
      }
    }
    return container;
  }

  private static Map<String, String> inheritedStyle(Element content, Element foreignObject) {
    var ancestors = new ArrayList<Element>();
    for (Element current = content;
        current != null && current != foreignObject;
        current = current.parent()) {
      ancestors.add(current);
    }
    Collections.reverse(ancestors);
    var inherited = new LinkedHashMap<String, String>();
    for (var ancestor : ancestors) inherited.putAll(style(ancestor.attr("style")));
    return inherited;
  }

  private static TextElement createText(
      Element content, Map<String, String> inherited, Map<String, String> box,
      double width, double height) {
    double specifiedFont = numeric(inherited.get("font-size"), 12);
    double fontSize = specifiedFont > 0 ? Math.min(512, specifiedFont) : 12;
    var text = new TextElement();
    text.fontSize = fontSize;
    text.width = width;
    text.height = Math.max(1, height);
    text.padding = new Padding();
    text.fontFamily = inherited.getOrDefault("font-family", "Arial")
        .replace("\"", "").replace("'", "");
    text.fill = SvgSanitizer.printColor(inherited.getOrDefault("color", "#000000"));
    text.lineHeight = numeric(inherited.get("line-height"), 1.2);
    if (text.lineHeight > 4) text.lineHeight /= fontSize;
    if (text.lineHeight <= 0) text.lineHeight = 1.2;
    text.align = inherited.getOrDefault("text-align",
        box.getOrDefault("justify-content", "").contains("center") ? "center" : "left");

    for (var descendant : content.getAllElements()) {
      if (descendant.hasAttr("style")) {
        descendant.attr("style", SvgSanitizer.printColor(descendant.attr("style")));
      }
    }
    String inline = "font-size:" + fontSize + "px;font-family:"
        + XmlEscape.escape(text.fontFamily) + ";color:" + text.fill
        + ";font-weight:" + inherited.getOrDefault("font-weight", "normal")
        + ";font-style:" + inherited.getOrDefault("font-style", "normal") + ";";
    text.html = "<div style=\"" + inline + "\">" + content.html() + "</div>";
    return text;
  }

  private static Map<String, String> style(String css) {
    var result = new LinkedHashMap<String, String>();
    for (String declaration : css.split(";")) {
      int colon = declaration.indexOf(':');
      if (colon > 0) {
        result.put(declaration.substring(0, colon).trim().toLowerCase(Locale.ROOT),
            declaration.substring(colon + 1).trim());
      }
    }
    return result;
  }

  private static double numeric(String value, double fallback) {
    if (value == null || value.isBlank() || value.endsWith("%")) return fallback;
    try {
      double number = Double.parseDouble(value.trim().replaceFirst("px$", ""));
      return Double.isFinite(number) ? number : fallback;
    } catch (NumberFormatException error) {
      return fallback;
    }
  }
}
