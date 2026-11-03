package ru.ticket.generation.infrastructure;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import ru.ticket.dto.Slide;
import ru.ticket.export.ExportFormat;
import ru.ticket.generation.application.dto.NamedDocument;
import ru.ticket.generation.application.port.DocumentRenderer;
import ru.ticket.image.ImageEmbedder;
import ru.ticket.svg.SvgDocumentBuilder;

@Component
public class SvgDocumentRenderer implements DocumentRenderer {

  private final SvgDocumentBuilder builder;
  private final ImageEmbedder imageEmbedder;

  public SvgDocumentRenderer(SvgDocumentBuilder builder, ImageEmbedder imageEmbedder) {
    this.builder = builder;
    this.imageEmbedder = imageEmbedder;
  }

  @Override
  public ExportFormat format() {
    return ExportFormat.SVG;
  }

  @Override
  public byte[] render(Slide slide) {
    String svg = imageEmbedder.embed(builder.generate(slide));
    return svg.getBytes(StandardCharsets.UTF_8);
  }

  /**
   * SVG — не multi-page формат. На каждый слайд создаём отдельный файл: slides.size() == 1 →
   * base.svg slides.size() > 1 → base-p1.svg, base-p2.svg, …
   */
  @Override
  public List<NamedDocument> renderMulti(List<Slide> slides, String filenameBase) {
    return renderMulti(slides, filenameBase, new ru.ticket.dto.ExportOptions());
  }

  @Override
  public List<NamedDocument> renderMulti(
      List<Slide> slides, String filenameBase, ru.ticket.dto.ExportOptions options) {
    List<NamedDocument> out = new ArrayList<>(slides.size());
    boolean multi = slides.size() > 1;
    String ext = extension();

    for (int i = 0; i < slides.size(); i++) {
      String name = multi ? filenameBase + "-p" + (i + 1) + ext : filenameBase + ext;
      String svg = builder.generate(slides.get(i));
      if (options == null || options.embedImages) svg = imageEmbedder.embed(svg);
      if (options != null && options.prettyPrint) {
        try {
          var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
          factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
          factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
          factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
          var document =
              factory
                  .newDocumentBuilder()
                  .parse(new java.io.ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
          var transformerFactory = javax.xml.transform.TransformerFactory.newInstance();
          transformerFactory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
          transformerFactory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
          var transformer = transformerFactory.newTransformer();
          transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
          var output = new java.io.StringWriter();
          transformer.transform(
              new javax.xml.transform.dom.DOMSource(document),
              new javax.xml.transform.stream.StreamResult(output));
          svg = output.toString();
        } catch (Exception error) {
          throw new IllegalArgumentException("Cannot format SVG", error);
        }
      }
      out.add(new NamedDocument(name, svg.getBytes(StandardCharsets.UTF_8)));
    }
    return out;
  }

  @Override
  public String mimeType() {
    return "image/svg+xml";
  }

  @Override
  public String extension() {
    return ".svg";
  }
}
