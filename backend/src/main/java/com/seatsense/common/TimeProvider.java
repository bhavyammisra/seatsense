package com.seatsense.common;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * The only source of "now" in the app. Inject this instead of calling Instant.now() or LocalDateTime.now(),
 * so tests can use a fixed clock and the dev simulator can fast-forward time.
 */
public class TimeProvider {

    private final Clock base;
    private volatile Duration offset = Duration.ZERO;

    public TimeProvider(Clock base) {
        this.base = base;
    }

    public Instant now() {
        return clock().instant();
    }

    /** For LocalDate.now(clock), LocalTime.now(clock) and similar. Includes any fast-forward. */
    public Clock clock() {
        return Clock.offset(base, offset);
    }

    /** Moves time forward. Only tests and the dev simulator should call this. */
    public synchronized void advance(Duration amount) {
        if (amount.isNegative()) {
            throw new IllegalArgumentException("Time can only move forward");
        }
        offset = offset.plus(amount);
    }
}
