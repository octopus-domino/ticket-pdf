package ru.ticket.routing.domain;

/** Expected outcomes that the HTTP adapter translates to the public error contract. */
public final class RoutingFailure extends RuntimeException {
    public enum Reason {
        INVALID_ROUTE("invalid-route"), CAR_TOO_LONG("too-long"), WALK_TOO_LONG("walk-too-long"),
        NO_PATH("no-path"), BUSY("busy"), RATE_LIMITED("rate-limited"), UNAVAILABLE("unavailable");

        private final String code;
        Reason(String code) { this.code = "journey.route." + code; }
    }

    private final Reason reason;

    public RoutingFailure(Reason reason) {
        super(reason.code);
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
