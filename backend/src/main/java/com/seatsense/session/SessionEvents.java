package com.seatsense.session;

import java.time.Instant;

/**
 * Domain events the session module publishes (docs/api.md, "Domain events").
 * Other modules only listen. Listeners that push to screens should use
 * {@code @TransactionalEventListener} so nothing is announced before it is saved.
 */
public final class SessionEvents {

    private SessionEvents() {
    }

    /** A student sat down. */
    public record SeatTaken(String seatCode, String floorId, Long studentId, Long sessionId, Instant at) {
    }

    /** A seat became free again. */
    public record SeatFreed(String seatCode, String floorId, EndReason reason, Instant at) {
    }
}
