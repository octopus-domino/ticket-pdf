package ru.ticket.svg.renderer;

import ru.ticket.dto.Element;
import ru.ticket.svg.SvgContext;

/**
 * Стратегия рендера одного типа элемента.
 *
 * Open/Closed: чтобы добавить новый тип элемента,
 * достаточно создать новую реализацию — SvgGenerator
 * трогать не нужно.
 */
public interface ElementRenderer<T extends Element> {

    /** Тип элемента, который умеет рендерить стратегия. */
    Class<T> supports();

    /** Отрисовать элемент в контекст. */
    void render(T element, SvgContext ctx);

    /**
     * Приоритет. Если несколько рендеров подходят,
     * побеждает меньший приоритет (более специфичный).
     */
    default int priority() { return 100; }
}