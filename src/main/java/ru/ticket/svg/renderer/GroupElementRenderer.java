package ru.ticket.svg.renderer;

import ru.ticket.dto.Element;
import ru.ticket.dto.GroupElement;
import ru.ticket.svg.SvgContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class GroupElementRenderer implements ElementRenderer<GroupElement> {

    private final ElementRendererRegistry registry;

    public GroupElementRenderer(@Lazy ElementRendererRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Class<GroupElement> supports() { return GroupElement.class; }

    @Override
    public void render(GroupElement g, SvgContext ctx) {
        if (g.children == null || g.children.isEmpty()) return;

        StringBuilder out = ctx.body();
        out.append(String.format(Locale.ROOT,
                "<g transform=\"translate(%.2f,%.2f) rotate(%.2f)\">\n",
                g.x, g.y, g.rotation));

        for (String childId : g.children) {
            Element child = ctx.slide().elements.stream()
                    .filter(e -> childId.equals(e.id))
                    .findFirst().orElse(null);
            if (child == null) continue;

            Element copy = deepCopy(child);
            // Локальная позиция: абсолютная ребёнка минус исходная позиция группы.
            // originalX() корректно возвращает исходную позицию для вложенных групп.
            copy.x = child.originalX() - g.originalX();
            copy.y = child.originalY() - g.originalY();

            @SuppressWarnings({"rawtypes", "unchecked"})
            ElementRenderer renderer = registry.findForType(copy.getClass());
            if (renderer != null) {
                renderer.render(copy, ctx);
            }
        }

        out.append("</g>\n");
    }

    /**
     * Deep-copy через рефлексию. Копирует все не-transient поля по цепочке наследования.
     * BeanUtils.copyProperties здесь не работает: наши DTO используют public-поля
     * без геттеров и сеттеров, а BeanUtils читает только через геттеры/сеттеры.
     */
    private Element deepCopy(Element src) {
        try {
            Element copy = src.getClass().getDeclaredConstructor().newInstance();
            for (Field f : allFields(src.getClass())) {
                f.setAccessible(true);
                f.set(copy, f.get(src));
            }
            copy.origX = src.originalX();
            copy.origY = src.originalY();
            return copy;
        } catch (Exception e) {
            return src;
        }
    }

    private List<Field> allFields(Class<?> cls) {
        List<Field> list = new ArrayList<>();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (Modifier.isTransient(f.getModifiers())) continue;
                if (Modifier.isStatic(f.getModifiers())) continue;
                list.add(f);
            }
            cls = cls.getSuperclass();
        }
        return list;
    }
}