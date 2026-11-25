package ru.ticket.export.impl;

import ru.ticket.dto.ExportRequest;
import ru.ticket.dto.Slide;
import ru.ticket.export.DocumentExporter;
import ru.ticket.export.ExportFormat;
import ru.ticket.export.ExportResult;
import ru.ticket.image.ImageEmbedder;
import ru.ticket.pdf.SvgToPdfConverter;
import ru.ticket.svg.SvgDocumentBuilder;
import ru.ticket.util.FilenameEncoder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PdfADocumentExporter implements DocumentExporter {

    private final SvgDocumentBuilder builder;
    private final ImageEmbedder imageEmbedder;
    private final SvgToPdfConverter pdfConverter;
    private final FilenameEncoder filenameEncoder;

    public PdfADocumentExporter(
            SvgDocumentBuilder builder,
            ImageEmbedder imageEmbedder,
            @Qualifier("pdfASvgToPdfConverter") SvgToPdfConverter pdfConverter,
            FilenameEncoder filenameEncoder) {
        this.builder = builder;
        this.imageEmbedder = imageEmbedder;
        this.pdfConverter = pdfConverter;
        this.filenameEncoder = filenameEncoder;
    }

    @Override
    public ExportFormat format() { return ExportFormat.PDFA; }

    @Override
    public ExportResult export(ExportRequest request) {
        if (request.slides == null || request.slides.isEmpty()) {
            throw new IllegalArgumentException("PDF/A export: no slides provided");
        }

        try {
            List<String> svgs = new ArrayList<>(request.slides.size());
            for (Slide slide : request.slides) {
                svgs.add(imageEmbedder.embed(builder.generate(slide)));
            }

            byte[] pdf = pdfConverter.convertMultiPage(svgs);

            String name = request.slides.size() == 1
                    ? request.slides.get(0).name
                    : "document";

            return new ExportResult(
                    pdf,
                    filenameEncoder.build(name, ".pdfa.pdf"),
                    "application/pdf"
            );
        } catch (Exception e) {
            throw new RuntimeException("PDF/A export failed: " + e.getMessage(), e);
        }
    }
}