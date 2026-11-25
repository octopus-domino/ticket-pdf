package ru.ticket.routing.application;

import ru.ticket.routing.domain.Road;
import ru.ticket.routing.domain.RoadRequest;

/** One road calculation strategy. Implementations own their provider's protocol and cooldown. */
@FunctionalInterface
public interface RoadProvider {
    Road calculate(RoadRequest request);
}
