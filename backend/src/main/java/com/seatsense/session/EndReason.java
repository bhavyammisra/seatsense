package com.seatsense.session;

/** Why a session ended: the {@code end_reason} column (V2) and the {@code SeatFreed} reason (docs/api.md). */
public enum EndReason {
    /** The student scanned out and chose Leaving. */
    LEAVE,
    /** The student was on a break and did not come back in time. */
    BREAK_EXPIRED,
    /** The library closed for the day. */
    CLOSING,
    /** Staff freed the seat (staff sweep, S4.3). */
    ADMIN
}
