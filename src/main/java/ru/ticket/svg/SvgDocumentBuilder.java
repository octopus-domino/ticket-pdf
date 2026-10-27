package ru.ticket.svg;

import ru.ticket.dto.*;
import ru.ticket.svg.renderer.ElementRenderer;
import ru.ticket.svg.renderer.ElementRendererRegistry;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class SvgDocumentBuilder {

    private final ElementRendererRegistry registry;

    public SvgDocumentBuilder(ElementRendererRegistry registry) {
        this.registry = registry;
    }

    public String generate(Slide slide) {
        SvgContext ctx = new SvgContext(slide);

        appendBackground(ctx);

        // Карта id -> элемент для clipPathId
        Map<String, Element> byId = new HashMap<>();
        for (Element el : slide.elements) {
            if (el.id != null) byId.put(el.id, el);
        }

        // Множество id детей групп, чтобы не рендерить дважды
        Set<String> groupChildIds = new HashSet<>();
        for (Element el : slide.elements) {
            if (el instanceof GroupElement group) {
                groupChildIds.addAll(group.children);
            }
        }

        // 1. Заливки (fill)
        for (Element el : slide.elements) {
            if (el instanceof FillElement && !groupChildIds.contains(el.id)) {
                renderWithClip(el, ctx, byId);
            }
        }

        // 2. Остальные (кроме детей групп)
        for (Element el : slide.elements) {
            if (!(el instanceof FillElement) && !groupChildIds.contains(el.id)) {
                renderWithClip(el, ctx, byId);
            }
        }

        return wrapDocument(slide, ctx);
    }

    /**
     * Обёртка над renderElement: если у элемента есть clipPathId
     * и этот clipper — shape, то:
     *   1) кладём clipPath в defs (один раз на id);
     *   2) оборачиваем render в <g clip-path="url(#...)">.
     *
     * Сам clipper-элемент остаётся в slide.elements и рендерится
     * как обычная фигура (как во frontend serialize.ts), чтобы
     * пользователь видел контур-маску.
     */
    private void renderWithClip(Element el, SvgContext ctx, Map<String, Element> byId) {
        if (el == null || el.clipPathId == null || el.clipPathId.isBlank()) {
            renderElement(el, ctx);
            return;
        }

        Element clipper = byId.get(el.clipPathId);
        if (!(clipper instanceof ShapeElement shape)) {
            // clipper отсутствует или не shape — рендерим без обрезки
            renderElement(el, ctx);
            return;
        }

        String pathD = SvgClipUtil.shapeToPath(shape);
        if (pathD == null) {
            renderElement(el, ctx);
            return;
        }

        String clipId = "clip-" + el.id;
        ctx.appendDefs(String.format(Locale.ROOT,
                "<clipPath id=\"%s\" clipPathUnits=\"userSpaceOnUse\">" +
                        "<path d=\"%s\"/></clipPath>",
                clipId, pathD));

        // Рендер во временный буфер, чтобы потом обернуть в <g>
        StringBuilder target = ctx.body();
        int start = target.length();
        renderElement(el, ctx);
        int end = target.length();

        if (end > start) {
            String inner = target.substring(start, end);
            target.setLength(start);
            target.append("<g clip-path=\"url(#")
                    .append(clipId)
                    .append(")\">\n")
                    .append(inner)
                    .append("</g>\n");
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void renderElement(Element el, SvgContext ctx) {
        ElementRenderer renderer = registry.findForType(el.getClass());
        if (renderer != null) {
            renderer.render(el, ctx);
        }
    }

    private void appendBackground(SvgContext ctx) {
        Slide slide = ctx.slide();
        StringBuilder out = ctx.body();

        if (slide.backgroundImage != null && !slide.backgroundImage.isBlank()) {
            out.append(SvgSerializationUtil.imageTag(
                    slide.backgroundImage, 0, 0, slide.width, slide.height,
                    " preserveAspectRatio=\"xMidYMid slice\""));
            return;
        }

        String bg = slide.background == null ? "#ffffff" : slide.background;
        if (!"transparent".equals(bg)) {
            out.append(String.format(Locale.ROOT,
                    "<rect x=\"0\" y=\"0\" width=\"%.2f\" height=\"%.2f\" fill=\"%s\"/>\n",
                    slide.width, slide.height, bg));
        }
    }

    private String wrapDocument(Slide slide, SvgContext ctx) {
        double vw = slide.width;
        double vh = slide.height;

        StringBuilder doc = new StringBuilder();
        doc.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        doc.append(String.format(Locale.ROOT,
                "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" " +
                        "width=\"%.2f\" height=\"%.2f\" viewBox=\"0 0 %.2f %.2f\">\n",
                vw, vh, vw, vh));

        if (ctx.hasDefs()) {
            doc.append("<defs>\n").append(ctx.defsXml()).append("</defs>\n");
        }
        doc.append(ctx.body());
        doc.append("</svg>\n");
        return doc.toString();
    }
}