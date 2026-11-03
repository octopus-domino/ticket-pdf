package ru.ticket.svg.renderer.stamp;

import java.util.Locale;
import org.springframework.stereotype.Component;
import ru.ticket.dto.StampImageItem;
import ru.ticket.svg.SvgSerializationUtil;

/** Serializes the image geometry stored by the stamp editor. */
@Component
public class StampImageWriter {
    public void write(StringBuilder out, StampImageItem image) {
        if (Boolean.TRUE.equals(image.hidden) || image.src == null || image.src.isBlank()) return;
        String aspect = "fill".equals(image.fit) ? "none"
                : "cover".equals(image.fit) ? "xMidYMid slice" : "xMidYMid meet";
        double rotation = image.rotation == null ? 0 : image.rotation;
        String transform = rotation == 0 ? "" : String.format(Locale.ROOT,
                " transform=\"rotate(%.2f %.2f %.2f)\"", rotation, image.x, image.y);
        out.append("<g").append(transform).append(">\n");
        out.append(SvgSerializationUtil.imageTag(image.src, image.x, image.y,
                image.width, image.height, " preserveAspectRatio=\"" + aspect + "\" overflow=\"hidden\""));
        out.append("</g>\n");
    }
}
