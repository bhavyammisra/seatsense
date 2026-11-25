package com.seatsense.common;

import com.seatsense.session.SeatState;
import java.time.Instant;

/**
 * Implemented by every domain event that changes what a seat looks like on the map (SeatTaken, SeatFreed,
 * BreakStarted, BreakEnded, SeatHeld, HoldReleased, SeatServiceChanged in docs/api.md). The live module listens
 * for this one type and pushes {@code { code, state, at }} to /topic/seats/{floorId}, so it needs no change when
 * a new event is added.
 */
public interface SeatStateEvent {

    String seatCode();

    String floorId();

    /** What the seat is now. */
    SeatState newState();

    Instant at();
}
