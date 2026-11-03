package ru.ticket.svg;

import java.io.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;

/** Parse, allowlist and serialize at the server boundary; XML parsing alone is not sanitization. */
public final class SvgSanitizer {
  private static final Set<String> TAGS =
      Set.of(
          "svg",
          "g",
          "defs",
          "symbol",
          "use",
          "path",
          "rect",
          "circle",
          "ellipse",
          "line",
          "polyline",
          "polygon",
          "text",
          "tspan",
          "textPath",
          "image",
          "clipPath",
          "mask",
          "linearGradient",
          "radialGradient",
          "stop",
          "pattern",
          "title",
          "desc",
          "foreignObject",
          "div",
          "span",
          "p",
          "br",
          "b",
          "strong",
          "i",
          "em",
          "table",
          "tbody",
          "tr",
          "td");
  private static final Set<String> CSS =
      Set.of(
          "font-family",
          "font-size",
          "font-weight",
          "font-style",
          "text-decoration",
          "text-align",
          "white-space",
          "line-height",
          "color",
          "background-color",
          "fill",
          "stroke",
          "stroke-width",
          "opacity",
          "display",
          "overflow",
          "width",
          "height",
          "margin",
          "padding",
          "border");

  private SvgSanitizer() {}

  public static String sanitize(String svg) {
    try {
      var factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      var doc =
          factory
              .newDocumentBuilder()
              .parse(
                  new ByteArrayInputStream(svg.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      if (!"svg".equals(doc.getDocumentElement().getLocalName()))
        throw new IllegalArgumentException("SVG root required");
      clean(doc.getDocumentElement());
      var transformer = TransformerFactory.newInstance();
      transformer.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
      var writer = new StringWriter();
      transformer.newTransformer().transform(new DOMSource(doc), new StreamResult(writer));
      return writer.toString();
    } catch (Exception error) {
      throw new IllegalArgumentException("Invalid SVG", error);
    }
  }

  private static void clean(Element element) {
    for (int i = element.getAttributes().getLength() - 1; i >= 0; i--) {
      Node attr = element.getAttributes().item(i);
      String key = attr.getNodeName().toLowerCase(Locale.ROOT);
      String value = attr.getNodeValue().trim();
      boolean remove =
          key.startsWith("on")
              || key.equals("srcdoc")
              || value.contains("\\")
              || value.toLowerCase(Locale.ROOT).contains("javascript:");
      if (key.equals("style")) element.setAttribute(attr.getNodeName(), style(value));
      if (key.equals("href") || key.equals("xlink:href") || key.equals("src")) {
        boolean image = element.getLocalName() != null && element.getLocalName().equals("image");
        remove |=
            !(value.startsWith("#")
                || image
                    && (value.matches("(?is)data:image/(png|jpeg|gif|webp);base64,[a-z0-9+/=\\s]+")
                        || value.matches("https?://[^\\s]+")));
      }
      if (!key.equals("style")
          && value.toLowerCase(Locale.ROOT).contains("url(")
          && !value.matches("url\\(#[a-zA-Z0-9_.:-]+\\)")) remove = true;
      if (remove) element.removeAttributeNode((Attr) attr);
    }
    for (Node child = element.getFirstChild(); child != null; ) {
      Node next = child.getNextSibling();
      if (child instanceof Element nested) {
        String name = nested.getLocalName() == null ? nested.getTagName() : nested.getLocalName();
        if (!TAGS.contains(name)) element.removeChild(nested);
        else clean(nested);
      }
      child = next;
    }
  }

  private static String style(String css) {
    StringBuilder result = new StringBuilder();
    for (String declaration : css.split(";")) {
      String[] pair = declaration.split(":", 2);
      if (pair.length != 2) continue;
      String key = pair[0].trim().toLowerCase(Locale.ROOT), value = pair[1].trim();
      if (CSS.contains(key)
          && value.matches("[a-zA-Z0-9#.,%()'\" /-]+")
          && !value.toLowerCase(Locale.ROOT).contains("url(")
          && !value.toLowerCase(Locale.ROOT).contains("expression"))
        result.append(key).append(':').append(value).append(';');
    }
    return result.toString();
  }
}
