package com.seatsense.waitlist;

/** A waitlist request the rules refuse. {@link #errorCode()} is the api.md {@code error} value. */
public class WaitlistException extends RuntimeException {

    private final String errorCode;
    private final int status;

    private WaitlistException(String errorCode, int status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String errorCode() {
        return errorCode;
    }

    public int status() {
        return status;
    }

    static WaitlistException alreadyWaiting() {
        return new WaitlistException("ALREADY_WAITING", 409, "You are already on the waitlist.");
    }

    static WaitlistException alreadyInside() {
        return new WaitlistException("ALREADY_INSIDE", 409, "You already have a seat in the library.");
    }

    static WaitlistException noOffer() {
        return new WaitlistException("NO_OFFER", 409, "There is no seat offer to accept right now.");
    }

    static WaitlistException noSuchFloor(String floorId) {
        return new WaitlistException("NOT_FOUND", 404, "No floor " + floorId + ".");
    }

    static WaitlistException notAStudent() {
        return new WaitlistException("FORBIDDEN", 403, "This account is not linked to a student.");
    }

    static WaitlistException badAlertNumber(int max) {
        return new WaitlistException("VALIDATION_FAILED", 400, "Choose a number of free seats from 1 to " + max + ".");
    }

    static WaitlistException alreadyFree(String floorName, int free) {
        return new WaitlistException("ALREADY_FREE", 409, floorName + " already has " + free + " free seats. You can go now.");
    }
}
