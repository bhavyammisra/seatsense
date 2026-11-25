package com.seatsense.gate;

import com.seatsense.session.SeatSession;
import com.seatsense.session.SessionStatus;
import java.time.Instant;
import java.util.UUID;

/** JSON shapes of the gate and session API (docs/api.md, "Gate"). */
public final class GateViews {

    private GateViews() {
    }

    /** A student as the kiosk shows them. */
    public record StudentView(Long id, String name, String initials, boolean sample) {
    }

    public record SessionView(
        Long id,
        String seat,
        SessionStatus status,
        Instant startedAt,
        Instant breakUntil,
        int breaksUsedToday,
        UUID groupId) {

        static SessionView of(SeatSession s, String seatCode, int breaksUsedToday) {
            return new SessionView(s.getId(), seatCode, s.getStatus(), s.getStartedAt(), s.getBreakUntil(),
                breaksUsedToday, s.getGroupId());
        }
    }

    /** Response of POST /api/gate/scan, and the body of the SCANNED message to the kiosk. */
    public record ScanResult(
        String type,
        ScanMode mode,
        StudentView student,
        String lastSeat,
        String heldSeat,
        SessionView session) {
    }
}
