package ru.ticket.routing.application;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import ru.ticket.routing.domain.*;
import static org.junit.jupiter.api.Assertions.*;
import static ru.ticket.routing.domain.RoutingFailure.Reason.*;

class CalculateRoadTest {
    private final RoadRequest route = new RoadRequest(new RoadRequest.Point(30, 59),
            new RoadRequest.Point(30.01, 59), RoadRequest.Mode.FOOT);
    private final Road answer = new Road(List.of(List.of(30d, 59d), List.of(30.01, 59d)), 600, 400);

    @Test void triesProvidersInOrderAndStopsOnSuccess() {
        RoadProvider failed = request -> { throw new RoutingFailure(NO_PATH); };
        var service = new CalculateRoad(List.of(failed, request -> answer, request -> fail()));
        assertSame(answer, service.calculate(route));
    }

    @Test void reportsLastFailureAndReleasesCapacity() {
        var service = new CalculateRoad(List.of(request -> { throw new RoutingFailure(RATE_LIMITED); }));
        for (int i = 0; i < 2; i++)
            assertEquals(RATE_LIMITED, assertThrows(RoutingFailure.class, () -> service.calculate(route)).reason());
        assertEquals(UNAVAILABLE, assertThrows(RoutingFailure.class,
                () -> new CalculateRoad(List.of()).calculate(route)).reason());
    }

    @Test void rejectsConcurrentCalculationWithoutQueueing() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        var service = new CalculateRoad(List.of(request -> {
            started.countDown();
            try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
            catch (InterruptedException e) { throw new AssertionError(e); }
            return answer;
        }));
        try {
            var first = executor.submit(() -> service.calculate(route));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertEquals(BUSY, assertThrows(RoutingFailure.class, () -> service.calculate(route)).reason());
            release.countDown();
            assertSame(answer, first.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
