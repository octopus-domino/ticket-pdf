package ru.ticket.email.render;

import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;

/**
 * Adapter: Batik {@link PNGTranscoder}.
 *
 * <p>Output width = SVG user-unit width × scaleFactor. Aspect ratio is
 * preserved automatically because Batik honors the viewBox.
 */
@Service
public class BatikSlideRasterizer implements SlideRasterizer {

    /** SVGs produced by {@code SvgDocumentBuilder} use 1 user unit = 1 px @ 96 DPI. */
    private static final float PX_TO_MM = 25.4f / 96f;

    @Override
    public byte[] rasterizePng(String svg, double scaleFactor) throws Exception {
        if (svg == null || svg.isBlank()) {
            throw new IllegalArgumentException("Cannot rasterize empty SVG");
        }
        double scale = scaleFactor > 0 ? scaleFactor : 1.0;
        double svgWidth = extractWidth(svg);
        if (svgWidth <= 0) {
            throw new IllegalArgumentException("SVG has invalid width attribute");
        }

        PNGTranscoder transcoder = new PNGTranscoder();
        transcoder.addTranscodingHint(PNGTranscoder.KEY_PIXEL_UNIT_TO_MILLIMETER, PX_TO_MM);
        transcoder.addTranscodingHint(
                PNGTranscoder.KEY_WIDTH, (float) (svgWidth * scale));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        transcoder.transcode(
                new TranscoderInput(new StringReader(svg)),
                new TranscoderOutput(out));
        return out.toByteArray();
    }

    private double extractWidth(String svg) {
        int idx = svg.indexOf(" width=\"");
        if (idx < 0) return 0;
        int start = idx + " width=\"".length();
        int end = svg.indexOf('"', start);
        if (end < 0) return 0;
        try {
            return Double.parseDouble(svg.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}