package ru.ticket.email.render;

/**
 * Port: rasterizes a self-contained SVG into PNG bytes.
 *
 * <p>Reasons to keep this as an interface: Batik may be swapped for a
 * different rasterizer (e.g. Apache Batik → Cairo, or a native-image path)
 * without touching {@link SlideHtmlRenderer}.
 */
public interface SlideRasterizer {

    /**
     * @param svg          self-contained SVG document (no external refs)
     * @param scaleFactor  pixel density multiplier (1.0 = 96 DPI, 2.0 = retina)
     * @return PNG bytes
     * @throws Exception  if the SVG cannot be parsed or rendered
     */
    byte[] rasterizePng(String svg, double scaleFactor) throws Exception;
}