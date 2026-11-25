package com.seatsense.gate;

import com.seatsense.common.TimeProvider;
import com.seatsense.gate.GateViews.ScanResult;
import com.seatsense.gate.GateViews.SessionView;
import com.seatsense.gate.GateViews.StudentView;
import com.seatsense.session.SeatSession;
import com.seatsense.session.SessionService;
import com.seatsense.session.SessionStatus;
import com.seatsense.student.Student;
import com.seatsense.student.StudentService;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * The gate scan (WORKPLAN S1.5). The phone scanner only reports "this card was
 * scanned here"; the server decides what it means from the student's session:
 * <ul>
 *   <li>no open session: ENTRY (kiosk shows the seat picker)</li>
 *   <li>ACTIVE: EXIT (kiosk asks Leaving or Break)</li>
 *   <li>ON_BREAK, break not over: RETURN (back to the same seat, done here)</li>
 *   <li>ON_BREAK, break over: the session is ended as expired, then ENTRY</li>
 * </ul>
 * Every scan is logged, unknown cards included, and announced to the kiosk.
 */
@Service
public class GateService {

    private final StudentService students;
    private final SessionService sessions;
    private final ScanEventRepository scans;
    private final ApplicationEventPublisher events;
    private final TimeProvider time;

    public GateService(StudentService students, SessionService sessions, ScanEventRepository scans,
                       ApplicationEventPublisher events, TimeProvider time) {
        this.students = students;
        this.sessions = sessions;
        this.scans = scans;
        this.events = events;
        this.time = time;
    }

    /**
     * Handles a scan.
     *
     * @throws UnknownCardException the card is not in the student table
     */
    // No outer transaction on purpose: each session call commits on its own, and the scan log row is
    // saved by its repository, so a refused return cannot roll back the scan record.
    public ScanResult scan(String cardId, String gateId) {
        Optional<Student> found = students.findByCardId(cardId);
        if (found.isEmpty()) {
            scans.save(new ScanEvent(trimmed(cardId), gateId, null, "UNKNOWN_CARD", time.now()));
            throw new UnknownCardException();
        }
        Student student = found.get();

        ScanMode mode = ScanMode.ENTRY;
        SessionView session = null;
        Optional<SeatSession> open = sessions.openSessionOf(student.getId());
        if (open.isPresent()) {
            SeatSession current = open.get();
            if (current.getStatus() == SessionStatus.ACTIVE) {
                mode = ScanMode.EXIT;
                session = view(current);
            } else if (current.getStatus() == SessionStatus.ON_BREAK) {
                try {
                    SeatSession back = sessions.returnFromBreak(current.getId());
                    mode = ScanMode.RETURN;
                    session = view(back);
                } catch (RuntimeException breakRanOut) {
                    // The break expired and the seat was freed: a normal entry.
                    mode = ScanMode.ENTRY;
                }
            }
        }

        scans.save(new ScanEvent(student.getCardId(), gateId, student.getId(), mode.name(), time.now()));
        ScanResult result = new ScanResult(
            "SCANNED",
            mode,
            new StudentView(student.getId(), student.getName(), student.getInitials(), student.isSample()),
            mode == ScanMode.ENTRY ? sessions.lastSeatOf(student.getId()).orElse(null) : null,
            null, // held seats arrive with the waitlist (B2.x)
            session);
        events.publishEvent(new CardScanned(gateId, result));
        return result;
    }

    SessionView view(SeatSession session) {
        return SessionView.of(session, sessions.seatCodeOf(session), (int) sessions.breaksUsedToday(session.getStudentId()));
    }

    private static String trimmed(String cardId) {
        String value = cardId == null ? "" : cardId.strip();
        return value.length() > 32 ? value.substring(0, 32) : value;
    }

    /** 404 UNKNOWN_CARD. */
    public static class UnknownCardException extends RuntimeException {
        UnknownCardException() {
            super("This card is not registered with the library.");
        }
    }
}
