package ru.ticket.email.render;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.ticket.dto.Slide;
import ru.ticket.image.ImageEmbedder;
import ru.ticket.svg.SvgDocumentBuilder;

/**
 * Renders a single slide as pixel-perfect HTML for both preview and email.
 *
 * <p><b>Design</b>: both modes share the same source of truth — the SVG document produced by {@link
 * SvgDocumentBuilder}. Preview inlines it as a {@code data:image/svg+xml;base64} URL. Email
 * rasterizes it via {@link SlideRasterizer} and embeds the PNG through a CID reference so
 * Outlook/Gmail display exactly what the editor sees.
 *
 * <p>Because the rendering pipeline is a single SVG document, preview and email are guaranteed to
 * match the slide 1 : 1 (fonts, shapes, rich text, stamps, diagrams, gradients, arc-text —
 * everything supported by the SVG exporter).
 */
@Service
public class SlideHtmlRenderer {

  private static final Logger log = LoggerFactory.getLogger(SlideHtmlRenderer.class);

  /** Retina density for emailed PNGs (A4 → ~1588×2246 px). */
  private static final double EMAIL_SCALE = 2.0;

  /** Preview width in CSS pixels — the iframe scales down on small screens. */
  private static final int PREVIEW_WIDTH = 600;

  private final SvgDocumentBuilder svgBuilder;
  private final ImageEmbedder imageEmbedder;
  private final SlideRasterizer rasterizer;
  private final SlidePlainTextExtractor plainTextExtractor;

  public SlideHtmlRenderer(
      SvgDocumentBuilder svgBuilder,
      ImageEmbedder imageEmbedder,
      SlideRasterizer rasterizer,
      SlidePlainTextExtractor plainTextExtractor) {
    this.svgBuilder = svgBuilder;
    this.imageEmbedder = imageEmbedder;
    this.rasterizer = rasterizer;
    this.plainTextExtractor = plainTextExtractor;
  }

  /**
   * @param slide slide to render (must not be {@code null})
   * @param mode preview or email
   * @return immutable render result
   */
  public Result render(Slide slide, RenderMode mode) {
    if (slide == null) throw new IllegalArgumentException("slide must not be null");
    if (!Double.isFinite(slide.width)
        || !Double.isFinite(slide.height)
        || slide.width <= 0
        || slide.height <= 0
        || slide.width * slide.height * 4 > 16_000_000)
      throw new IllegalArgumentException("Email raster exceeds 16 million pixels");

    // 1. Build SVG — single source of truth for both modes.
    String svg = svgBuilder.generate(slide);

    // 2. Inline any remote http(s) images so the SVG is fully self-contained.
    //    Without this, both email PNG and preview SVG would lose external images.
    svg = imageEmbedder.embed(svg);

    String plainText = plainTextExtractor.extract(slide);

    return switch (mode) {
      case PREVIEW -> renderPreview(slide, svg, plainText);
      case EMAIL -> renderEmail(slide, svg, plainText);
    };
  }

  /* ============================================================
  Preview: inline SVG as base64 data URL. Vector. No external deps.
  ============================================================ */

  private Result renderPreview(Slide slide, String svg, String plainText) {
    String dataUrl =
        "data:image/svg+xml;base64,"
            + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));

    int width = PREVIEW_WIDTH;
    int height = Math.max(1, (int) Math.round(slide.height * width / slide.width));
    String title = escape(slide.name);

    String html =
        """
        <!DOCTYPE html>
        <html lang="ru"><head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width,initial-scale=1">
          <title>%s</title>
        </head><body style="margin:0;padding:16px;background:#f4f4f5;text-align:center;">
          <img src="%s" width="%d" height="%d" alt="%s"
               style="max-width:100%%;height:auto;display:inline-block;border:0;">
        </body></html>
        """
            .formatted(title, dataUrl, width, height, title);

    return new Result(html, Map.of(), plainText);
  }

  /* ============================================================
  Email: rasterize to PNG @2x, embed via CID inline attachment.
  ============================================================ */

  private Result renderEmail(Slide slide, String svg, String plainText) {
    byte[] png;
    try {
      png = rasterizer.rasterizePng(svg, EMAIL_SCALE);
    } catch (Exception e) {
      log.error("slide.render.rasterization_failed slide={} name={}", slide.id, slide.name, e);
      // Graceful degradation — still shows the document in the inbox
      // (albeit with weaker client support), instead of an empty message.
      return renderPreview(slide, svg, plainText);
    }

    String cid = "slide-1";
    String title = escape(slide.name);

    String html =
        """
        <!DOCTYPE html>
        <html lang="ru"><head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width,initial-scale=1">
          <title>%s</title>
        </head><body style="margin:0;padding:16px;background:#f4f4f5;">
          <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0">
            <tr><td align="center">
              <img src="cid:%s" width="600" alt="%s"
                   style="max-width:100%%;height:auto;display:block;border:0;">
            </td></tr>
          </table>
        </body></html>
        """
            .formatted(title, cid, title);

    return new Result(html, Map.of(cid, png), plainText);
  }

  /* ============================================================ */

  private String escape(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }

  /**
   * Immutable render result.
   *
   * @param html full HTML document
   * @param inlineImages CID → bytes; empty in PREVIEW mode
   * @param plainText text/plain alternative for the MIME message
   */
  public record Result(String html, Map<String, byte[]> inlineImages, String plainText) {}
}
