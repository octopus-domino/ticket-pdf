package ru.ticket.routing.application;

import java.util.List;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.ticket.routing.domain.Road;
import ru.ticket.routing.domain.RoadRequest;
import ru.ticket.routing.domain.RoutingFailure;
import static ru.ticket.routing.domain.RoutingFailure.Reason.*;

/** Tries enabled providers in configured order. Expensive calculations never queue. */
public final class CalculateRoad {
    private static final Logger log = LoggerFactory.getLogger(CalculateRoad.class);
    private final List<RoadProvider> providers;
    private final Semaphore capacity = new Semaphore(1);

    public CalculateRoad(List<RoadProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    public Road calculate(RoadRequest request) {
        if (!capacity.tryAcquire()) throw new RoutingFailure(BUSY);
        try {
            RoutingFailure last = new RoutingFailure(UNAVAILABLE);
            for (RoadProvider provider : providers) {
                try {
                    return provider.calculate(request);
                } catch (RoutingFailure failure) {
                    if (Thread.currentThread().isInterrupted()) throw failure;
                    last = failure;
                    log.warn("Routing provider {} failed: {}", provider.getClass().getSimpleName(), failure.reason());
                }
            }
            throw last;
        } finally {
            capacity.release();
        }
    }
}
