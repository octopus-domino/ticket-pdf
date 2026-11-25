package ru.ticket.export.impl;

import ru.ticket.dto.ExportRequest;
import ru.ticket.dto.Slide;
import ru.ticket.export.DocumentExporter;
import ru.ticket.export.ExportFormat;
import ru.ticket.export.ExportResult;
import ru.ticket.image.ImageEmbedder;
import ru.ticket.svg.SvgDocumentBuilder;
import ru.ticket.util.FilenameEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class SvgDocumentExporter implements DocumentExporter {

    private final SvgDocumentBuilder builder;
    private final ImageEmbedder imageEmbedder;
    private final FilenameEncoder filenameEncoder;

    public SvgDocumentExporter(SvgDocumentBuilder builder,
                               ImageEmbedder imageEmbedder,
                               FilenameEncoder filenameEncoder) {
        this.builder = builder;
        this.imageEmbedder = imageEmbedder;
        this.filenameEncoder = filenameEncoder;
    }

    @Override
    public ExportFormat format() { return ExportFormat.SVG; }

    @Override
    public ExportResult export(ExportRequest request) {
        if (request.slides == null || request.slides.isEmpty()) {
            throw new IllegalArgumentException("SVG export: no slides provided");
        }
        Slide slide = request.slides.get(0);
        String svg = imageEmbedder.embed(builder.generate(slide));

        return new ExportResult(
                svg.getBytes(StandardCharsets.UTF_8),
                filenameEncoder.build(slide.name, ".svg"),
                "image/svg+xml"
        );
    }
}