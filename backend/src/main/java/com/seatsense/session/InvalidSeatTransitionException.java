package com.seatsense.session;

/**
 * Thrown when an event is not allowed in the seat's current state, for example
 * starting a break on a free seat. The API reports it as 409 INVALID_TRANSITION.
 */
public class InvalidSeatTransitionException extends RuntimeException {

    private final SeatState from;
    private final SeatEvent event;

    public InvalidSeatTransitionException(SeatState from, SeatEvent event) {
        super("Cannot apply " + event + " to a seat that is " + from);
        this.from = from;
        this.event = event;
    }

    public SeatState getFrom() {
        return from;
    }

    public SeatEvent getEvent() {
        return event;
    }
}
