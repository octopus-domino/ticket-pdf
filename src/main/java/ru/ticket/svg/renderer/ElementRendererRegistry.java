package ru.ticket.svg.renderer;

import ru.ticket.dto.Element;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class ElementRendererRegistry {

    private final List<ElementRenderer<?>> renderers;

    public ElementRendererRegistry(List<ElementRenderer<?>> renderers) {
        this.renderers = renderers.stream()
                .sorted(Comparator.comparingInt(ElementRenderer::priority))
                .toList();
    }

    /**
     * Ищет рендер для класса. Сначала точное совпадение,
     * затем — по иерархии (первый рендер, чей supports()
     * является предком переданного класса).
     */
    public ElementRenderer<?> findForType(Class<?> type) {
        for (ElementRenderer<?> r : renderers) {
            if (r.supports() == type) return r;
        }
        for (ElementRenderer<?> r : renderers) {
            if (r.supports().isAssignableFrom(type)) return r;
        }
        return null;
    }

    /** Совместимость со старой версией (использовалась раньше). */
    @SuppressWarnings("unchecked")
    public <T extends Element> ElementRenderer<T> find(Class<T> type) {
        return (ElementRenderer<T>) findForType(type);
    }
}