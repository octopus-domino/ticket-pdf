package ru.ticket.svg.renderer;

import ru.ticket.dto.ImageElement;
import ru.ticket.svg.SvgContext;
import ru.ticket.svg.SvgSerializationUtil;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class ImageElementRenderer implements ElementRenderer<ImageElement> {

    @Override
    public Class<ImageElement> supports() { return ImageElement.class; }

    @Override
    public void render(ImageElement img, SvgContext ctx) {
        if (img.src == null) return;

        StringBuilder out = ctx.body();
        String tr = img.rotation != 0
                ? String.format(Locale.ROOT, " transform=\"rotate(%.2f %.2f %.2f)\"",
                img.rotation, img.x, img.y)
                : "";

        out.append("<g").append(tr).append(">\n");
        out.append(SvgSerializationUtil.imageTag(
                img.src, img.x, img.y, img.width, img.height,
                " preserveAspectRatio=\"xMidYMid meet\""));
        out.append("</g>\n");
    }
}