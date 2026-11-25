package ru.ticket.svg;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Генератор уникальных id для определений SVG
 * (clipPath, textPath, mask).
 *
 * Потокобезопасен, т.к. при параллельном экспорте
 * несколько рендеров могут обращаться одновременно.
 */
public final class DefsCollector {

    private final AtomicInteger counter = new AtomicInteger();

    public String nextId(String prefix) {
        return prefix + "-" + counter.incrementAndGet();
    }

    public String shapeClipId(String targetId) {
        return "clip-" + targetId;
    }
}