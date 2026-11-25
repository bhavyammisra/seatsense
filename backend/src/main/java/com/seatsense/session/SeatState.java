package com.seatsense.session;

/**
 * The state of one seat, as every screen shows it (docs/api.md, Seat.state).
 */
public enum SeatState {

    /** Nobody is using it. The only state a student can pick at the gate. */
    FREE,

    /** Kept for one person on the waitlist for a limited time. */
    HELD,

    /** Someone is sitting here. */
    TAKEN,

    /** The person stepped out on a break and is coming back. */
    ON_BREAK,

    /** Closed by the library (broken chair, cleaning). */
    OUT_OF_SERVICE;

    /** True when someone has a claim on the seat right now. */
    public boolean isClaimed() {
        return this == HELD || this == TAKEN || this == ON_BREAK;
    }
}
