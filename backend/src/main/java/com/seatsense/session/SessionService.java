package com.seatsense.session;

import com.seatsense.allocation.BestSeatRanker;
import com.seatsense.allocation.RankingRequest;
import com.seatsense.allocation.ScoredSeat;
import com.seatsense.allocation.SeatPreferences;
import com.seatsense.common.TimeProvider;
import com.seatsense.layout.Seat;
import com.seatsense.layout.SeatRepository;
import com.seatsense.session.SessionEvents.SeatFreed;
import com.seatsense.session.SessionEvents.SeatTaken;
import com.seatsense.student.StudentRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seat sessions (WORKPLAN S1.4): taking a seat, leaving it, and the current
 * state of every seat for the map.
 *
 * <p><b>One seat, one person.</b> Before inserting, the service checks the seat
 * with {@link SeatStateMachine}, which gives a clear answer in the normal case.
 * Two people can still pass that check at the same moment, so the real guarantee
 * is V2's partial unique indexes: Postgres rejects the second insert. The insert
 * runs in its own transaction; when it is rejected, the service reads which
 * index fired and, for a taken seat, suggests the nearest free seat in a fresh
 * transaction (a rejected one cannot be read from any more).
 */
@Service
public class SessionService implements SeatStateSource {

    static final String ONE_OPEN_PER_SEAT = "seat_session_one_open_per_seat";
    static final String ONE_OPEN_PER_STUDENT = "seat_session_one_open_per_student";

    private final SeatSessionRepository sessions;
    private final SeatRepository seats;
    private final StudentRepository students;
    private final SeatCatalog catalog;
    private final ApplicationEventPublisher events;
    private final TimeProvider time;
    private final TransactionTemplate write;
    private final TransactionTemplate read;
    private final BestSeatRanker ranker = new BestSeatRanker();

    public SessionService(
        SeatSessionRepository sessions,
        SeatRepository seats,
        StudentRepository students,
        SeatCatalog catalog,
        ApplicationEventPublisher events,
        TimeProvider time,
        PlatformTransactionManager transactions) {
        this.sessions = sessions;
        this.seats = seats;
        this.students = students;
        this.catalog = catalog;
        this.events = events;
        this.time = time;
        this.write = new TransactionTemplate(transactions);
        this.read = new TransactionTemplate(transactions);
        this.read.setReadOnly(true);
    }

    /**
     * The student sits at the seat.
     *
     * @throws SessionException.SeatTaken       someone else has it (with a suggested alternative)
     * @throws SessionException.AlreadyInside   the student already has a seat
     * @throws SessionException.SeatUnavailable the seat is out of service
     * @throws SessionException.NotFound        no such seat or student
     */
    public SeatSession takeSeat(Long studentId, String seatCode) {
        try {
            return write.execute(status -> insertSession(studentId, seatCode));
        } catch (DataIntegrityViolationException rejected) {
            if (violates(rejected, ONE_OPEN_PER_SEAT)) {
                throw new SessionException.SeatTaken(seatCode, alternativeNear(seatCode).orElse(null));
            }
            if (violates(rejected, ONE_OPEN_PER_STUDENT)) {
                throw new SessionException.AlreadyInside(studentId);
            }
            throw rejected;
        }
    }

    private SeatSession insertSession(Long studentId, String seatCode) {
        if (studentId == null || !students.existsById(studentId)) {
            throw new SessionException.NotFound("student " + studentId);
        }
        Seat seat = seats.findByCode(seatCode).orElseThrow(() -> new SessionException.NotFound("seat " + seatCode));
        if (sessions.findOpenByStudent(studentId).isPresent()) {
            throw new SessionException.AlreadyInside(studentId);
        }

        SeatState current = currentState(seat);
        if (current == SeatState.OUT_OF_SERVICE) {
            throw new SessionException.SeatUnavailable(seatCode);
        }
        if (!SeatStateMachine.canApply(current, SeatEvent.TAKE)) {
            throw new SessionException.SeatTaken(seatCode, alternativeNearInCurrentTransaction(seatCode).orElse(null));
        }
        SeatStateMachine.next(current, SeatEvent.TAKE);

        Instant now = time.now();
        SeatSession session = sessions.saveAndFlush(new SeatSession(studentId, seat.getId(), now, null));
        events.publishEvent(new SeatTaken(seat.getCode(), seat.getFloorId(), studentId, session.getId(), now));
        return session;
    }

    /**
     * The student scans out and chooses Leaving. Only an ACTIVE session can be
     * left this way: a student on break who scans in is returning (S2.4).
     *
     * @throws SessionException.NotFound        no such session
     * @throws InvalidSeatTransitionException   the session is on break or already over
     */
    public SeatSession leave(Long sessionId) {
        return write.execute(status -> {
            SeatSession session = sessions.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new SessionException.NotFound("session " + sessionId));
            SeatState seatState = session.isOpen() ? session.getStatus().seatState() : SeatState.FREE;
            SeatStateMachine.next(seatState, SeatEvent.LEAVE);

            Instant now = time.now();
            session.end(EndReason.LEAVE, now);
            Seat seat = seats.findById(session.getSeatId()).orElseThrow();
            events.publishEvent(new SeatFreed(seat.getCode(), seat.getFloorId(), EndReason.LEAVE, now));
            return session;
        });
    }

    /** The student's open session, if they are inside. The gate uses this to tell an entry from an exit. */
    public Optional<SeatSession> openSessionOf(Long studentId) {
        return read.execute(status -> sessions.findOpenByStudent(studentId));
    }

    /** Code of the seat the student used most recently, for "Same seat as last time". */
    public Optional<String> lastSeatOf(Long studentId) {
        return read.execute(status -> sessions.findFirstByStudentIdOrderByStartedAtDesc(studentId)
            .flatMap(s -> seats.findById(s.getSeatId()))
            .map(Seat::getCode));
    }

    /** Seat states for the map (implements {@link SeatStateSource}): TAKEN and ON_BREAK seats by code. */
    @Override
    public Map<String, SeatState> claimedSeats(String floorId) {
        return read.execute(status -> sessions.findOpenSeatsOnFloor(floorId).stream()
            .collect(Collectors.toMap(
                SeatSessionRepository.OpenSeat::getSeatCode,
                o -> o.getStatus().seatState())));
    }

    /** The nearest free seat to {@code seatCode}, using the best-seat ranking with "near" set. */
    Optional<String> alternativeNear(String seatCode) {
        return read.execute(status -> alternativeNearInCurrentTransaction(seatCode));
    }

    private Optional<String> alternativeNearInCurrentTransaction(String seatCode) {
        Map<String, SeatState> states = new HashMap<>();
        for (SeatSessionRepository.OpenSeat open : sessions.findAllOpenSeats()) {
            states.put(open.getSeatCode(), open.getStatus().seatState());
        }
        seats.findAll().stream()
            .filter(Seat::isOutOfService)
            .forEach(s -> states.put(s.getCode(), SeatState.OUT_OF_SERVICE));
        return ranker.best(catalog.allCandidates(), states, catalog.entrances(),
                new RankingRequest(SeatPreferences.NONE, null, seatCode))
            .map(ScoredSeat::seat)
            .map(c -> c.code());
    }

    private SeatState currentState(Seat seat) {
        if (seat.isOutOfService()) {
            return SeatState.OUT_OF_SERVICE;
        }
        return sessions.findOpenBySeat(seat.getId())
            .map(s -> s.getStatus().seatState())
            .orElse(SeatState.FREE);
    }

    /** True if Postgres rejected the insert because of the named unique index. */
    static boolean violates(Throwable error, String indexName) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains(indexName)) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }
}
