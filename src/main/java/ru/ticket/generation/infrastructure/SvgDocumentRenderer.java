package ru.ticket.generation.infrastructure;

import org.springframework.stereotype.Component;
import ru.ticket.dto.Slide;
import ru.ticket.export.ExportFormat;
import ru.ticket.generation.application.dto.NamedDocument;
import ru.ticket.generation.application.port.DocumentRenderer;
import ru.ticket.image.ImageEmbedder;
import ru.ticket.svg.SvgDocumentBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class SvgDocumentRenderer implements DocumentRenderer {

    private final SvgDocumentBuilder builder;
    private final ImageEmbedder imageEmbedder;

    public SvgDocumentRenderer(SvgDocumentBuilder builder, ImageEmbedder imageEmbedder) {
        this.builder = builder;
        this.imageEmbedder = imageEmbedder;
    }

    @Override
    public ExportFormat format() { return ExportFormat.SVG; }

    @Override
    public byte[] render(Slide slide) {
        String svg = imageEmbedder.embed(builder.generate(slide));
        return svg.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * SVG — не multi-page формат.
     * На каждый слайд создаём отдельный файл:
     *   slides.size() == 1  →  base.svg
     *   slides.size() > 1   →  base-p1.svg, base-p2.svg, …
     */
    @Override
    public List<NamedDocument> renderMulti(List<Slide> slides, String filenameBase) {
        List<NamedDocument> out = new ArrayList<>(slides.size());
        boolean multi = slides.size() > 1;
        String ext = extension();

        for (int i = 0; i < slides.size(); i++) {
            String name = multi
                    ? filenameBase + "-p" + (i + 1) + ext
                    : filenameBase + ext;
            out.add(new NamedDocument(name, render(slides.get(i))));
        }
        return out;
    }

    @Override
    public String mimeType() { return "image/svg+xml"; }

    @Override
    public String extension() { return ".svg"; }
}