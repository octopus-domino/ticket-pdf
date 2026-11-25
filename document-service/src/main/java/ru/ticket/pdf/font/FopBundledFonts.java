package ru.ticket.pdf.font;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.apache.fop.configuration.Configuration;
import org.apache.fop.configuration.DefaultConfigurationBuilder;
import org.apache.fop.svg.PDFTranscoder;

/**
 * Explicit fonts prevent silent Base-14 fallback and missing Cyrillic glyphs in printable
 * workflows.
 */
public final class FopBundledFonts {
  private static final Configuration CONFIGURATION = configuration();

  private FopBundledFonts() {}

  public static void configure(PDFTranscoder transcoder) throws Exception {
    transcoder.configure(CONFIGURATION);
  }

  private static Configuration configuration() {
    try {
      var xml = new StringBuilder("<cfg><fonts><auto-detect/>");
      for (String[] family :
          new String[][] {
            {"DejaVuSans", "DejaVu Sans", "Oblique"},
            {"DejaVuSerif", "DejaVu Serif", "Italic"},
            {"DejaVuSansMono", "DejaVu Sans Mono", "Oblique"}
          }) {
        for (int style = 0; style < 4; style++) {
          boolean bold = (style & 1) != 0, italic = (style & 2) != 0;
          String suffix =
              bold ? (italic ? "-Bold" + family[2] : "-Bold") : (italic ? "-" + family[2] : "");
          var url = FopBundledFonts.class.getResource("/fonts/" + family[0] + suffix + ".ttf");
          if (url == null) throw new IllegalStateException("Bundled PDF font is missing");
          try (var input = url.openStream()) {
            GraphicsEnvironment.getLocalGraphicsEnvironment()
                .registerFont(Font.createFont(Font.TRUETYPE_FONT, input));
          }
          xml.append("<font kerning=\"yes\" embedding-mode=\"subset\" embed-url=\"")
              .append(url.toExternalForm().replace("&", "&amp;").replace("\"", "&quot;"))
              .append("\"><font-triplet name=\"")
              .append(family[1])
              .append("\" style=\"")
              .append(italic ? "italic" : "normal")
              .append("\" weight=\"")
              .append(bold ? "bold" : "normal")
              .append("\"/></font>");
        }
      }
      for (String[] handwritten : new String[][] { { "MarckScript-Regular.ttf", "Marck Script" }, { "ZhiMangXing-Regular.ttf", "Zhi Mang Xing" } }) {
        var url = FopBundledFonts.class.getResource("/fonts/" + handwritten[0]);
        if (url == null) throw new IllegalStateException("Bundled handwriting font is missing");
        try (var input = url.openStream()) { GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(Font.createFont(Font.TRUETYPE_FONT, input)); }
        xml.append("<font kerning=\"yes\" embedding-mode=\"subset\" embed-url=\"").append(url.toExternalForm().replace("&", "&amp;").replace("\"", "&quot;"))
            .append("\"><font-triplet name=\"").append(handwritten[1]).append("\" style=\"normal\" weight=\"normal\"/></font>");
      }
      xml.append("</fonts></cfg>");
      return new DefaultConfigurationBuilder()
          .build(new ByteArrayInputStream(xml.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (Exception error) {
      throw new IllegalStateException("Cannot configure bundled PDF fonts", error);
    }
  }
}
