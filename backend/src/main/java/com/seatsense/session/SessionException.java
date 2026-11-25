package com.seatsense.session;

/**
 * A request the session rules refuse. {@link #errorCode()} is the {@code error}
 * value from docs/api.md, so the gate controllers (S1.5) can map it to a response.
 */
public abstract sealed class SessionException extends RuntimeException
    permits SessionException.SeatTaken, SessionException.AlreadyInside, SessionException.SeatUnavailable,
    SessionException.NotFound {

    private SessionException(String message) {
        super(message);
    }

    public abstract String errorCode();

    /** 409 SEAT_TAKEN: someone else has the seat. Carries the nearest free seat, if there is one. */
    public static final class SeatTaken extends SessionException {
        private final String seatCode;
        private final String alternative;

        public SeatTaken(String seatCode, String alternative) {
            super("Seat " + seatCode + " was taken a moment ago.");
            this.seatCode = seatCode;
            this.alternative = alternative;
        }

        public String seatCode() {
            return seatCode;
        }

        /** Code of the suggested seat, or null when the library is full. */
        public String alternative() {
            return alternative;
        }

        @Override
        public String errorCode() {
            return "SEAT_TAKEN";
        }
    }

    /** 409 ALREADY_INSIDE: the student already has a seat. */
    public static final class AlreadyInside extends SessionException {
        private final Long studentId;

        public AlreadyInside(Long studentId) {
            super("This student already has a seat.");
            this.studentId = studentId;
        }

        public Long studentId() {
            return studentId;
        }

        @Override
        public String errorCode() {
            return "ALREADY_INSIDE";
        }
    }

    /** 409 INVALID_TRANSITION: the seat is out of service and cannot be taken. */
    public static final class SeatUnavailable extends SessionException {
        public SeatUnavailable(String seatCode) {
            super("Seat " + seatCode + " is out of service.");
        }

        @Override
        public String errorCode() {
            return "INVALID_TRANSITION";
        }
    }

    /** 404 NOT_FOUND: no such seat, student or session. */
    public static final class NotFound extends SessionException {
        public NotFound(String what) {
            super("No " + what + ".");
        }

        @Override
        public String errorCode() {
            return "NOT_FOUND";
        }
    }
}
