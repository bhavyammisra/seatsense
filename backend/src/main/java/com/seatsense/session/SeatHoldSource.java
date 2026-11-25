package com.seatsense.session;

import java.util.Map;
import java.util.Optional;

/**
 * Seats held for someone on the waitlist (implemented by the waitlist module).
 * The session module reads it so held seats show as HELD and only the person a
 * seat is held for can take it. Until a bean exists, no seat is held.
 */
public interface SeatHoldSource {

    /** Every active hold: seat code to the student it is held for. */
    Map<String, Long> activeHolds();

    /** The seat held for this student, if any. */
    Optional<String> heldSeatOf(Long studentId);
}
