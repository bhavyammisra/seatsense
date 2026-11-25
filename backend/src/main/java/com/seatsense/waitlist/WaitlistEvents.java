package com.seatsense.waitlist;

import com.seatsense.common.SeatStateEvent;
import com.seatsense.session.SeatState;
import java.time.Instant;

/** Domain events the waitlist publishes (docs/api.md, SeatHeld and HoldReleased). */
public final class WaitlistEvents {

    private WaitlistEvents() {
    }

    /** A seat is now held for someone on the waitlist. */
    public record SeatHeld(String seatCode, String floorId, Long studentId, Instant until, Instant at)
        implements SeatStateEvent {

        @Override
        public SeatState newState() {
            return SeatState.HELD;
        }
    }

    /** A hold ended without the student sitting down (expired or cancelled): the seat is free. */
    public record HoldReleased(String seatCode, String floorId, String reason, Instant at) implements SeatStateEvent {

        @Override
        public SeatState newState() {
            return SeatState.FREE;
        }
    }

    /** Something to tell one student on the portal (docs/api.md, notification payloads). */
    record Notify(Long studentId, Object payload) {
    }
}
