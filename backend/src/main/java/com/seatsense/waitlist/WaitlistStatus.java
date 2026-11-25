package com.seatsense.waitlist;

/** A place in the waitlist (docs/api.md, GET /api/waitlist/me). */
public enum WaitlistStatus {
    /** In the queue. */
    WAITING,
    /** A seat is held for this student; they can accept it. */
    OFFERED,
    /** Accepted: the hold restarted at a fresh 15 minutes; they are walking over. */
    ACCEPTED,
    /** The offer ran out before they sat down. */
    EXPIRED,
    /** They left the queue. */
    CANCELLED,
    /** They sat down. */
    DONE;

    boolean isLive() {
        return this == WAITING || this == OFFERED || this == ACCEPTED;
    }
}
