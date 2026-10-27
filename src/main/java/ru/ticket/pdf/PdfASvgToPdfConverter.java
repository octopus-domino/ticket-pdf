package ru.ticket.pdf;

import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.fop.svg.PDFTranscoder;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.springframework.stereotype.Service;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service("pdfASvgToPdfConverter")
public class PdfASvgToPdfConverter implements SvgToPdfConverter {

    @Override
    public byte[] convert(String svg, float widthPx, float heightPx) throws Exception {
        byte[] rawPdf = convertRaw(svg);
        return makePdfA(rawPdf);
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
            byte[] rawPdf = convertRaw(svg);
            // PDFBox 3.x: addSource принимает RandomAccessRead, а не InputStream.
            merger.addSource(new RandomAccessReadBuffer(rawPdf));
        }

        merger.mergeDocuments(null);
        // PDF/A метаданные применяем к склеенному документу целиком.
        return makePdfA(baos.toByteArray());
    }

    /* ============================================================
       Внутренние шаги
       ============================================================ */

    private byte[] convertRaw(String svg) throws Exception {
        /* Шаг 1: SVG → PDF через Batik PDFTranscoder.
           Он не использует PdfBoxGraphics2D, поэтому проблема
           «Child context is missing a .dispose() call» не возникает.
           Текст рендерим в кривые — кириллица не зависит от системных шрифтов. */
        PDFTranscoder transcoder = new PDFTranscoder();
        transcoder.addTranscodingHint(PDFTranscoder.KEY_PIXEL_UNIT_TO_MILLIMETER, 25.4f / 96f);
        transcoder.addTranscodingHint(PDFTranscoder.KEY_STROKE_TEXT, true);
        transcoder.addTranscodingHint(PDFTranscoder.KEY_AUTO_FONTS, false);

        ByteArrayOutputStream intermediate = new ByteArrayOutputStream();
        transcoder.transcode(
                new TranscoderInput(new StringReader(svg)),
                new TranscoderOutput(intermediate)
        );
        return intermediate.toByteArray();
    }

    private byte[] makePdfA(byte[] rawPdf) throws Exception {
        /* Шаг 2: пост-обработка через PDFBox — добавляем OutputIntent
           и XMP-метаданные, превращая PDF в PDF/A-2b. */
        try (PDDocument doc = Loader.loadPDF(rawPdf)) {
            addSrgbOutputIntent(doc);
            setPdfAMetadata(doc);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private void addSrgbOutputIntent(PDDocument doc) {
        try {
            ICC_Profile profile = ICC_Profile.getInstance(ColorSpace.CS_sRGB);
            PDOutputIntent oi = new PDOutputIntent(
                    doc, new ByteArrayInputStream(profile.getData()));
            oi.setInfo("sRGB IEC61966-2.1");
            oi.setOutputCondition("sRGB IEC61966-2.1");
            oi.setOutputConditionIdentifier("sRGB IEC61966-2.1");
            oi.setRegistryName("http://www.color.org");
            doc.getDocumentCatalog().addOutputIntent(oi);
        } catch (Exception e) {
            System.err.println("PDF/A OutputIntent failed: " + e.getMessage());
        }
    }

    private void setPdfAMetadata(PDDocument doc) throws Exception {
        String xmp =
                "<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n" +
                        "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n" +
                        "  <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n" +
                        "    <rdf:Description rdf:about=\"\"\n" +
                        "        xmlns:dc=\"http://purl.org/dc/elements/1.1/\"\n" +
                        "        xmlns:pdf=\"http://ns.adobe.com/pdf/1.3/\"\n" +
                        "        xmlns:pdfaid=\"http://www.aiim.org/pdfa/ns/id/\">\n" +
                        "      <dc:format>application/pdf</dc:format>\n" +
                        "      <pdf:Producer>Ticket DocExport</pdf:Producer>\n" +
                        "      <pdfaid:part>2</pdfaid:part>\n" +
                        "      <pdfaid:conformance>B</pdfaid:conformance>\n" +
                        "    </rdf:Description>\n" +
                        "  </rdf:RDF>\n" +
                        "</x:xmpmeta>\n" +
                        "<?xpacket end=\"w\"?>\n";

        PDMetadata metadata = new PDMetadata(doc);
        metadata.importXMPMetadata(xmp.getBytes(StandardCharsets.UTF_8));
        doc.getDocumentCatalog().setMetadata(metadata);
    }
}