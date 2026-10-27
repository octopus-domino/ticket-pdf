package ru.ticket.pdf;

import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.fop.svg.PDFTranscoder;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service("batikSvgToPdfConverter")
public class BatikSvgToPdfConverter implements SvgToPdfConverter {

    @Override
    public byte[] convert(String svg, float widthPx, float heightPx) throws Exception {
        PDFTranscoder transcoder = new PDFTranscoder();
        transcoder.addTranscodingHint(PDFTranscoder.KEY_PIXEL_UNIT_TO_MILLIMETER, 25.4f / 96f);
        transcoder.addTranscodingHint(PDFTranscoder.KEY_STROKE_TEXT, false);
        transcoder.addTranscodingHint(PDFTranscoder.KEY_AUTO_FONTS, true);

        TranscoderInput input = new TranscoderInput(
                new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        TranscoderOutput output = new TranscoderOutput(baos);

        transcoder.transcode(input, output);
        return baos.toByteArray();
    }

    @Override
    public byte[] convertMultiPage(List<String> svgs) throws Exception {
        if (svgs.isEmpty()) {
            throw new IllegalArgumentException("convertMultiPage: empty svg list");
        }

        PDFMergerUtility merger = new PDFMergerUtility();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        merger.setDestinationStream(baos);

        for (String svg : svgs) {
            byte[] pdfBytes = convert(svg, 0f, 0f);
            // PDFBox 3.x: addSource принимает RandomAccessRead, а не InputStream.
            merger.addSource(new RandomAccessReadBuffer(pdfBytes));
        }

        merger.mergeDocuments(null);
        return baos.toByteArray();
    }
}