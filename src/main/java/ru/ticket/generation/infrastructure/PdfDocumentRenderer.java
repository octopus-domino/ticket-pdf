package ru.ticket.generation.infrastructure;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.ticket.dto.Slide;
import ru.ticket.export.ExportFormat;
import ru.ticket.generation.application.dto.NamedDocument;
import ru.ticket.generation.application.port.DocumentRenderer;
import ru.ticket.image.ImageEmbedder;
import ru.ticket.pdf.SvgToPdfConverter;
import ru.ticket.svg.SvgDocumentBuilder;

import java.util.ArrayList;
import java.util.List;

@Component
public class PdfDocumentRenderer implements DocumentRenderer {

    private final SvgDocumentBuilder builder;
    private final ImageEmbedder imageEmbedder;
    private final SvgToPdfConverter converter;

    public PdfDocumentRenderer(SvgDocumentBuilder builder,
                               ImageEmbedder imageEmbedder,
                               @Qualifier("batikSvgToPdfConverter") SvgToPdfConverter converter) {
        this.builder = builder;
        this.imageEmbedder = imageEmbedder;
        this.converter = converter;
    }

    @Override
    public ExportFormat format() { return ExportFormat.PDF; }

    @Override
    public byte[] render(Slide slide) {
        try {
            String svg = imageEmbedder.embed(builder.generate(slide));
            return converter.convertMultiPage(List.of(svg));
        } catch (Exception e) {
            throw new RuntimeException("PDF render failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<NamedDocument> renderMulti(List<Slide> slides, String filenameBase) {
        try {
            List<String> svgs = new ArrayList<>(slides.size());
            for (Slide s : slides) {
                svgs.add(imageEmbedder.embed(builder.generate(s)));
            }
            byte[] pdf = converter.convertMultiPage(svgs);
            return List.of(new NamedDocument(filenameBase + extension(), pdf));
        } catch (Exception e) {
            throw new RuntimeException("PDF multi-page render failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String mimeType() { return "application/pdf"; }

    @Override
    public String extension() { return ".pdf"; }
}