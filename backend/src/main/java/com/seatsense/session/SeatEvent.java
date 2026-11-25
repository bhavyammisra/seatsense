package com.seatsense.session;

/**
 * Everything that can happen to a seat. Each event moves the seat from one
 * {@link SeatState} to another, or is refused (see {@link SeatStateMachine}).
 */
public enum SeatEvent {

    /** A student takes the seat at the gate (a free seat, or the seat held for them). */
    TAKE,

    /** The waitlist offers the seat to the next person and holds it for them. */
    HOLD,

    /** The hold ends without being used: the offer expired or was cancelled. */
    RELEASE_HOLD,

    /** The student scans out and chooses Break. */
    START_BREAK,

    /** The student scans back in before the break ends. */
    RETURN,

    /** The break ran out and the student did not come back. */
    BREAK_EXPIRED,

    /** The student scans out and chooses Leaving. */
    LEAVE,

    /** The library closes for the day and every claimed seat is cleared. */
    CLOSE,

    /** Staff take a free seat out of use. */
    MARK_OUT_OF_SERVICE,

    /** Staff put the seat back into use. */
    RETURN_TO_SERVICE
}
