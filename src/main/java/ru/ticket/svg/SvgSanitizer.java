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
          "switch",
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

  /** Keep uploaded vector logos as sanitized vectors, not unsupported SVG-in-image data URLs. */
  public static String inlineSvgImage(String url, double x, double y, double width, double height, String extra) {
    try {
      int comma = url.indexOf(',');
      if (comma < 0 || url.length() > 3_000_000) throw new IllegalArgumentException("Invalid SVG image data");
      String header = url.substring(0, comma);
      String content = header.endsWith(";base64")
          ? new String(Base64.getDecoder().decode(url.substring(comma + 1)), java.nio.charset.StandardCharsets.UTF_8)
          : java.net.URLDecoder.decode(url.substring(comma + 1).replace("+", "%2B"), java.nio.charset.StandardCharsets.UTF_8);
      var root = parse(sanitize(content)).getDocumentElement();
      if (!root.hasAttribute("viewBox")) {
        String w = root.getAttribute("width").replaceFirst("px$", "");
        String h = root.getAttribute("height").replaceFirst("px$", "");
        if (!w.matches("[0-9.]+") || !h.matches("[0-9.]+")) throw new IllegalArgumentException("SVG image requires a viewBox or numeric dimensions");
        root.setAttribute("viewBox", "0 0 " + w + " " + h);
      }
      root.setAttribute("x", Double.toString(x)); root.setAttribute("y", Double.toString(y));
      root.setAttribute("width", Double.toString(width)); root.setAttribute("height", Double.toString(height));
      root.removeAttribute("preserveAspectRatio");
      root.removeAttribute("overflow");
      var writer = new StringWriter();
      var transformer = TransformerFactory.newInstance().newTransformer();
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
      transformer.transform(new DOMSource(root), new StreamResult(writer));
      return writer.toString().replaceFirst("<svg", "<svg" + java.util.regex.Matcher.quoteReplacement(extra == null ? "" : extra));
    } catch (Exception error) {
      throw new IllegalArgumentException("Invalid embedded SVG image", error);
    }
  }

  private static Document parse(String svg) throws Exception {
    var factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    return factory.newDocumentBuilder().parse(new ByteArrayInputStream(svg.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  }

  public static String sanitize(String svg) {
    try {
      var doc = parse(svg);
      if(doc.getElementsByTagNameNS("http://www.w3.org/2000/svg","foreignObject").getLength()>0)doc=parse(DrawioLabelNormalizer.normalize(svg));
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
      if (Set.of("fill","stroke","color","stop-color","flood-color").contains(key)) {
        value=printColor(value);element.setAttribute(attr.getNodeName(),value);
      }
      if ((key.equals("fill") || key.equals("stroke")) && value.equalsIgnoreCase("transparent"))
        element.setAttribute(attr.getNodeName(), "none");
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
      String key = pair[0].trim().toLowerCase(Locale.ROOT), value = printColor(pair[1].trim());
      if ((key.equals("fill") || key.equals("stroke")) && value.equalsIgnoreCase("transparent"))
        value = "none";
      if (CSS.contains(key)
          && value.matches("[a-zA-Z0-9#.,%()'\" /-]+")
          && !value.toLowerCase(Locale.ROOT).contains("url(")
          && !value.toLowerCase(Locale.ROOT).contains("expression"))
        result.append(key).append(':').append(value).append(';');
    }
    return result.toString();
  }
  /** Batik implements SVG 1.1 CSS. Resolve modern theme colors for a white printed page. */
  static String printColor(String value) {
    for(int iteration=0;iteration<16;iteration++) {
      int start=value.toLowerCase(Locale.ROOT).indexOf("light-dark(");if(start<0)return value;
      int depth=1,comma=-1,end=-1;
      for(int i=start+11;i<value.length();i++) {char c=value.charAt(i);if(c=='(')depth++;else if(c==')'){if(--depth==0){end=i;break;}}else if(c==','&&depth==1&&comma<0)comma=i;}
      if(comma<0||end<0)throw new IllegalArgumentException("A diagram contains an invalid light/dark color. Choose a solid color and export again.");
      value=value.substring(0,start)+value.substring(start+11,comma).trim()+value.substring(end+1);
    }
    throw new IllegalArgumentException("A diagram contains too many nested theme colors. Choose solid colors and export again.");
  }
}
