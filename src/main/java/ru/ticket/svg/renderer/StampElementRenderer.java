package ru.ticket.svg.renderer;

import ru.ticket.dto.StampElement;
import ru.ticket.dto.StampImageItem;
import ru.ticket.dto.StampShapeItem;
import ru.ticket.dto.StampTextItem;
import ru.ticket.svg.SvgContext;
import ru.ticket.svg.SvgSerializationUtil;
import ru.ticket.svg.renderer.stamp.AnchorFillWriter;
import ru.ticket.svg.renderer.stamp.LegacyStampWriter;
import ru.ticket.svg.renderer.stamp.StampShapeWriter;
import ru.ticket.svg.renderer.stamp.StampTextWriter;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class StampElementRenderer implements ElementRenderer<StampElement> {

    private final StampShapeWriter shapeWriter;
    private final StampTextWriter textWriter;
    private final AnchorFillWriter anchorFillWriter;
    private final LegacyStampWriter legacyWriter;

    public StampElementRenderer(StampShapeWriter shapeWriter,
                                StampTextWriter textWriter,
                                AnchorFillWriter anchorFillWriter,
                                LegacyStampWriter legacyWriter) {
        this.shapeWriter = shapeWriter;
        this.textWriter = textWriter;
        this.anchorFillWriter = anchorFillWriter;
        this.legacyWriter = legacyWriter;
    }

    @Override
    public Class<StampElement> supports() { return StampElement.class; }

    @Override
    public void render(StampElement stamp, SvgContext ctx) {
        // Konva.Group вращает вокруг левого верхнего угла после translate.
        String tr = String.format(Locale.ROOT,
                " transform=\"translate(%.2f,%.2f) rotate(%.2f)\"",
                stamp.x, stamp.y, stamp.rotation);

        StringBuilder out = ctx.body();
        out.append("<g").append(tr).append(">\n");

        // Legacy-ветка: нет shapes, но есть shape.
        boolean hasShapes = stamp.shapes != null && !stamp.shapes.isEmpty();
        if (!hasShapes && stamp.shape != null) {
            legacyWriter.write(out, stamp, ctx);
            out.append("</g>\n");
            return;
        }

        Map<String, StampShapeItem> byId = indexShapes(stamp);

        // 1. Anchor-заливки
        if (stamp.shapes != null) {
            for (StampShapeItem s : stamp.shapes) {
                if (!"fill".equals(s.kind)) continue;
                if (Boolean.TRUE.equals(s.hidden)) continue;
                StampShapeItem target = byId.get(s.fillTargetShapeId);
                if (target == null) continue;

                double ax = target.x + (s.fillAnchorLocal != null
                        ? s.fillAnchorLocal.x : 0.5) * target.width;
                double ay = target.y + (s.fillAnchorLocal != null
                        ? s.fillAnchorLocal.y : 0.5) * target.height;

                anchorFillWriter.write(out, target, s.fill, ctx, stamp.shapes, ax, ay);
            }

            // 2. Обводки
            for (StampShapeItem s : stamp.shapes) {
                if ("fill".equals(s.kind)) continue;
                if (Boolean.TRUE.equals(s.hidden)) continue;
                shapeWriter.write(out, s);
            }
        }

        // 3. Изображения
        writeImages(out, stamp);

        // 4. Тексты
        if (stamp.texts != null) {
            for (StampTextItem t : stamp.texts) {
                if (Boolean.TRUE.equals(t.hidden)) continue;
                textWriter.write(out, t, ctx);
            }
        }

        out.append("</g>\n");
    }

    private Map<String, StampShapeItem> indexShapes(StampElement stamp) {
        Map<String, StampShapeItem> byId = new HashMap<>();
        if (stamp.shapes == null) return byId;
        for (StampShapeItem s : stamp.shapes) {
            if (s.id != null) byId.put(s.id, s);
        }
        return byId;
    }

    private void writeImages(StringBuilder out, StampElement stamp) {
        if (stamp.images == null) return;
        for (StampImageItem im : stamp.images) {
            if (Boolean.TRUE.equals(im.hidden)) continue;
            if (im.src == null) continue;
            out.append(SvgSerializationUtil.imageTag(
                    im.src, im.x, im.y, im.width, im.height,
                    " preserveAspectRatio=\"xMidYMid meet\""));
        }
    }
}