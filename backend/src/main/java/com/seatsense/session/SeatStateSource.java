package com.seatsense.session;

import java.util.Map;

/**
 * Where the current state of seats comes from (sessions and holds). The session
 * service (S1.4) implements this. Until a bean exists, every seat that is not
 * out of service reads as FREE.
 */
public interface SeatStateSource {

    /**
     * States of the seats on one floor that someone has a claim on (TAKEN,
     * ON_BREAK or HELD), by seat code. Seats not in the map are free.
     */
    Map<String, SeatState> claimedSeats(String floorId);
}
