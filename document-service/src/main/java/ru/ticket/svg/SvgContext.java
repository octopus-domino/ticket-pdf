package ru.ticket.svg;

import ru.ticket.dto.Slide;

/**
 * Value Object, который несёт состояние между рендерами.
 * Не содержит логики, только данные + вспомогательные буферы.
 *
 * Рендеры пишут в body и defs, читают slide и id-счётчики.
 */
public final class SvgContext {

    private final Slide slide;
    private final StringBuilder body = new StringBuilder();
    private final DefsCollector defs = new DefsCollector();
    private final StringBuilder defsBuffer = new StringBuilder();

    public SvgContext(Slide slide) {
        this.slide = slide;
    }

    public Slide slide()          { return slide; }
    public StringBuilder body()   { return body; }
    public DefsCollector defs()   { return defs; }

    public void appendDefs(String xml) {
        defsBuffer.append(xml).append('\n');
    }

    public String defsXml()       { return defsBuffer.toString(); }
    public boolean hasDefs()      { return defsBuffer.length() > 0; }
}