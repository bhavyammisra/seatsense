package com.seatsense.session;

/** The {@code status} column of {@code seat_session} (V2). */
public enum SessionStatus {
    /** The student is in the seat. */
    ACTIVE,
    /** The student stepped out on a break; the seat is still theirs. */
    ON_BREAK,
    /** Over. Kept for history ("same seat as last time", analytics, found items). */
    ENDED;

    /** The seat state this session gives its seat, or null once it has ended. */
    public SeatState seatState() {
        return switch (this) {
            case ACTIVE -> SeatState.TAKEN;
            case ON_BREAK -> SeatState.ON_BREAK;
            case ENDED -> null;
        };
    }
}
