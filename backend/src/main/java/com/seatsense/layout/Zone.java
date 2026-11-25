package com.seatsense.layout;

/** The kind of area a seat is in (docs/api.md, Seat.zone). */
public enum Zone {
    /** Silence expected. */
    QUIET,
    /** Normal reading area. */
    GENERAL,
    /** Group tables and rooms where talking is allowed. */
    DISCUSSION
}
