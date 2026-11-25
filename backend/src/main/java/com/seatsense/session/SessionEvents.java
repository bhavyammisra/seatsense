package com.seatsense.session;

import com.seatsense.common.SeatStateEvent;
import java.time.Instant;

/**
 * Domain events the session module publishes (docs/api.md, "Domain events").
 * Other modules only listen. Every event that changes how a seat looks
 * implements {@link SeatStateEvent}, so the live module pushes it to screens
 * after the transaction commits.
 */
public final class SessionEvents {

    private SessionEvents() {
    }

    /** A student sat down. */
    public record SeatTaken(String seatCode, String floorId, Long studentId, Long sessionId, Instant at)
        implements SeatStateEvent {

        @Override
        public SeatState newState() {
            return SeatState.TAKEN;
        }
    }

    /** A seat became free again. */
    public record SeatFreed(String seatCode, String floorId, EndReason reason, Instant at) implements SeatStateEvent {

        @Override
        public SeatState newState() {
            return SeatState.FREE;
        }
    }

    /** A student stepped out on a break; the seat stays theirs until {@code until}. */
    public record BreakStarted(String seatCode, String floorId, Long sessionId, Long studentId, Instant until, Instant at)
        implements SeatStateEvent {

        @Override
        public SeatState newState() {
            return SeatState.ON_BREAK;
        }
    }

    /** The student came back from a break in time. (A break that runs out is a {@link SeatFreed}.) */
    public record BreakEnded(String seatCode, String floorId, Long sessionId, Long studentId, Instant at)
        implements SeatStateEvent {

        @Override
        public SeatState newState() {
            return SeatState.TAKEN;
        }
    }
}
