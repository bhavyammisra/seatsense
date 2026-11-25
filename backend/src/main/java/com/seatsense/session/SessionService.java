package com.seatsense.session;

import com.seatsense.allocation.BestSeatRanker;
import com.seatsense.allocation.GroupOption;
import com.seatsense.allocation.GroupSeatFinder;
import com.seatsense.allocation.RankingRequest;
import com.seatsense.allocation.ScoredSeat;
import com.seatsense.allocation.SeatCandidate;
import com.seatsense.allocation.SeatPreferences;
import com.seatsense.common.TimeProvider;
import com.seatsense.layout.Floor;
import com.seatsense.layout.FloorRepository;
import com.seatsense.layout.Seat;
import com.seatsense.layout.SeatRepository;
import com.seatsense.session.SessionEvents.BreakEnded;
import com.seatsense.session.SessionEvents.BreakStarted;
import com.seatsense.session.SessionEvents.SeatFreed;
import com.seatsense.session.SessionEvents.SeatTaken;
import com.seatsense.student.StudentRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seat sessions (WORKPLAN S1.4, S2.1 to S2.5): taking a seat (chosen, best,
 * same as last time, or a group together), leaving, breaks, and the scheduled
 * break expiry and closing-time reset. Also the current state of every seat
 * for the map.
 *
 * <p><b>One seat, one person.</b> Before inserting, the service checks the seat
 * with {@link SeatStateMachine}, which gives a clear answer in the normal case.
 * Two people can still pass that check at the same moment, so the real guarantee
 * is V2's partial unique indexes: Postgres rejects the second insert. Inserts run
 * in their own transaction; when one is rejected, the service reads which index
 * fired and answers in a fresh transaction (a rejected one cannot be read from).
 */
@Service
public class SessionService implements SeatStateSource {

    static final String ONE_OPEN_PER_SEAT = "seat_session_one_open_per_seat";
    static final String ONE_OPEN_PER_STUDENT = "seat_session_one_open_per_student";

    /** How many ranked seats "best seat" tries if the best ones are taken in the same moment. */
    private static final int BEST_ATTEMPTS = 5;
    private static final int GROUP_OPTIONS = 8;

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final SeatSessionRepository sessions;
    private final SeatRepository seats;
    private final FloorRepository floors;
    private final StudentRepository students;
    private final SeatCatalog catalog;
    private final ApplicationEventPublisher events;
    private final TimeProvider time;
    private final SessionSettings settings;
    private final ObjectProvider<SeatHoldSource> holdSource;
    private final TransactionTemplate write;
    private final TransactionTemplate read;
    private final BestSeatRanker ranker = new BestSeatRanker();
    private final GroupSeatFinder groupFinder = new GroupSeatFinder(ranker);

    public SessionService(
        SeatSessionRepository sessions,
        SeatRepository seats,
        FloorRepository floors,
        StudentRepository students,
        SeatCatalog catalog,
        ApplicationEventPublisher events,
        TimeProvider time,
        SessionSettings settings,
        ObjectProvider<SeatHoldSource> holdSource,
        PlatformTransactionManager transactions) {
        this.sessions = sessions;
        this.seats = seats;
        this.floors = floors;
        this.students = students;
        this.catalog = catalog;
        this.events = events;
        this.time = time;
        this.settings = settings;
        this.holdSource = holdSource;
        this.write = new TransactionTemplate(transactions);
        this.read = new TransactionTemplate(transactions);
        this.read.setReadOnly(true);
    }

    // ---------------------------------------------------------------- taking seats

    /**
     * The student sits at the seat they chose.
     *
     * @throws SessionException.SeatTaken       someone else has it (with a suggested alternative)
     * @throws SessionException.AlreadyInside   the student already has a seat
     * @throws SessionException.SeatUnavailable the seat is out of service
     * @throws SessionException.NotFound        no such seat or student
     */
    public SeatSession takeSeat(Long studentId, String seatCode) {
        try {
            return write.execute(status -> insertSession(studentId, seatCode, null));
        } catch (DataIntegrityViolationException rejected) {
            throw translate(rejected, studentId, seatCode);
        }
    }

    /**
     * "Best seat for me" (S2.1): the top-ranked free seat. If someone takes it in
     * the same moment, the next ranked seats are tried.
     *
     * @throws SessionException.LibraryFull every seat is taken
     */
    public SeatSession takeBest(Long studentId, SeatPreferences preferences) {
        return takeFirstFree(studentId, read.execute(status -> rankFreeSeats(preferences, null, BEST_ATTEMPTS)));
    }

    /** "Same seat as last time" (S2.2): the previous seat if it is free, otherwise the best seat near it. */
    public SeatSession takeSameAsLast(Long studentId) {
        Optional<String> last = lastSeatOf(studentId);
        if (last.isEmpty()) {
            return takeBest(studentId, SeatPreferences.NONE);
        }
        try {
            return takeSeat(studentId, last.get());
        } catch (SessionException.SeatTaken | SessionException.SeatUnavailable gone) {
            return takeFirstFree(studentId, read.execute(status ->
                rankFreeSeats(SeatPreferences.NONE, last.get(), BEST_ATTEMPTS)));
        }
    }

    private SeatSession takeFirstFree(Long studentId, List<String> ranked) {
        if (ranked.isEmpty()) {
            throw new SessionException.LibraryFull();
        }
        SessionException.SeatTaken lastLoss = null;
        for (String code : ranked) {
            try {
                return takeSeat(studentId, code);
            } catch (SessionException.SeatTaken lost) {
                lastLoss = lost; // taken in the same moment: try the next ranked seat
            }
        }
        throw lastLoss;
    }

    /**
     * "Sit together" (S2.3): every student gets a seat, or nobody does. With
     * {@code seatCodes}, exactly those seats; without, the best group the finder
     * suggests, trying the next suggestion if one is taken in the same moment.
     *
     * @throws SessionException.GroupNotAvailable no block of free seats fits the group
     * @throws SessionException.AlreadyInside     one of the students already has a seat
     */
    public List<SeatSession> takeGroup(List<Long> studentIds, List<String> seatCodes) {
        if (studentIds == null || studentIds.size() < GroupSeatFinder.MIN_GROUP
            || studentIds.size() > GroupSeatFinder.MAX_GROUP || new HashSet<>(studentIds).size() != studentIds.size()) {
            throw new IllegalArgumentException("A group is 2 to 4 different students");
        }
        if (seatCodes != null && !seatCodes.isEmpty()) {
            if (seatCodes.size() != studentIds.size() || new HashSet<>(seatCodes).size() != seatCodes.size()) {
                throw new IllegalArgumentException("Give one different seat for each student");
            }
            return insertGroupOrFail(studentIds, seatCodes);
        }

        List<List<String>> options = read.execute(status -> groupOptions(studentIds.size()));
        for (List<String> option : options) {
            try {
                return insertGroupOrFail(studentIds, option);
            } catch (SessionException.GroupNotAvailable takenMeanwhile) {
                // someone sat in one of these seats in the same moment: try the next option
            }
        }
        throw new SessionException.GroupNotAvailable(studentIds.size());
    }

    private List<SeatSession> insertGroupOrFail(List<Long> studentIds, List<String> seatCodes) {
        UUID groupId = UUID.randomUUID();
        try {
            return write.execute(status -> {
                List<SeatSession> created = new ArrayList<>();
                for (int i = 0; i < studentIds.size(); i++) {
                    try {
                        created.add(insertSession(studentIds.get(i), seatCodes.get(i), groupId));
                    } catch (SessionException.SeatTaken | SessionException.SeatUnavailable taken) {
                        throw new SessionException.GroupNotAvailable(studentIds.size());
                    }
                }
                return created;
            });
        } catch (DataIntegrityViolationException rejected) {
            if (violates(rejected, ONE_OPEN_PER_STUDENT)) {
                throw new SessionException.AlreadyInside(null);
            }
            throw new SessionException.GroupNotAvailable(studentIds.size());
        }
    }

    private SeatSession insertSession(Long studentId, String seatCode, UUID groupId) {
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
        if (current == SeatState.HELD && !studentId.equals(activeHolds().get(seatCode))) {
            // Held for someone else on the waitlist: treat it like a taken seat.
            throw new SessionException.SeatTaken(seatCode, alternativeNearInCurrentTransaction(seatCode).orElse(null));
        }
        if (!SeatStateMachine.canApply(current, SeatEvent.TAKE)) {
            throw new SessionException.SeatTaken(seatCode, alternativeNearInCurrentTransaction(seatCode).orElse(null));
        }
        SeatStateMachine.next(current, SeatEvent.TAKE);

        Instant now = time.now();
        SeatSession session = sessions.saveAndFlush(new SeatSession(studentId, seat.getId(), now, groupId));
        events.publishEvent(new SeatTaken(seat.getCode(), seat.getFloorId(), studentId, session.getId(), now));
        return session;
    }

    private RuntimeException translate(DataIntegrityViolationException rejected, Long studentId, String seatCode) {
        if (violates(rejected, ONE_OPEN_PER_SEAT)) {
            return new SessionException.SeatTaken(seatCode, alternativeNear(seatCode).orElse(null));
        }
        if (violates(rejected, ONE_OPEN_PER_STUDENT)) {
            return new SessionException.AlreadyInside(studentId);
        }
        return rejected;
    }

    // ---------------------------------------------------------------- leaving and breaks

    /**
     * The student scans out and chooses Leaving. Only an ACTIVE session can be
     * left this way: a student on break who scans in is returning.
     *
     * @throws SessionException.NotFound        no such session
     * @throws InvalidSeatTransitionException   the session is on break or already over
     */
    public SeatSession leave(Long sessionId) {
        return write.execute(status -> {
            SeatSession session = find(sessionId);
            SeatStateMachine.next(seatStateOf(session), SeatEvent.LEAVE);
            return end(session, EndReason.LEAVE);
        });
    }

    /**
     * The student scans out and chooses Break (S2.4). The seat stays theirs for
     * {@code seatsense.breaks.length}; breaks are limited per student per day,
     * counted across all of today's sessions.
     *
     * @throws SessionException.NoBreaksLeft  today's breaks are used
     * @throws InvalidSeatTransitionException the session is not ACTIVE
     */
    public SeatSession startBreak(Long sessionId) {
        return write.execute(status -> {
            SeatSession session = find(sessionId);
            SeatStateMachine.next(seatStateOf(session), SeatEvent.START_BREAK);
            if (countBreaksToday(session.getStudentId()) >= settings.maxPerDay()) {
                throw new SessionException.NoBreaksLeft(settings.maxPerDay());
            }
            Instant now = time.now();
            Instant until = now.plus(settings.length());
            session.startBreak(until);
            Seat seat = seats.findById(session.getSeatId()).orElseThrow();
            events.publishEvent(new BreakStarted(seat.getCode(), seat.getFloorId(), session.getId(),
                session.getStudentId(), until, now));
            return session;
        });
    }

    /**
     * The student is back before the break ends. If it already ran out, the
     * session is ended (BREAK_EXPIRED, seat freed) and the return is refused.
     *
     * @throws InvalidSeatTransitionException not on a break, or the break already ran out
     */
    public SeatSession returnFromBreak(Long sessionId) {
        SeatSession result = write.execute(status -> {
            SeatSession session = find(sessionId);
            SeatStateMachine.next(seatStateOf(session), SeatEvent.RETURN);
            if (time.now().isAfter(session.getBreakUntil())) {
                return end(session, EndReason.BREAK_EXPIRED);
            }
            session.returnFromBreak();
            Seat seat = seats.findById(session.getSeatId()).orElseThrow();
            events.publishEvent(new BreakEnded(seat.getCode(), seat.getFloorId(), session.getId(),
                session.getStudentId(), time.now()));
            return session;
        });
        if (!result.isOpen()) {
            throw new InvalidSeatTransitionException(SeatState.FREE, SeatEvent.RETURN);
        }
        return result;
    }

    /** Ends breaks that ran out (every 30 seconds). Returns how many ended. */
    @Scheduled(fixedDelayString = "${seatsense.breaks.check-every:PT30S}")
    public int expireBreaks() {
        Integer ended = write.execute(status -> {
            List<SeatSession> overdue = sessions.findByStatusAndBreakUntilBefore(SessionStatus.ON_BREAK, time.now());
            overdue.forEach(s -> end(s, EndReason.BREAK_EXPIRED));
            return overdue.size();
        });
        if (ended != null && ended > 0) {
            log.info("Ended {} break(s) that ran out", ended);
        }
        return ended == null ? 0 : ended;
    }

    /**
     * Closing-time reset (S2.5, every minute): on a floor whose {@code close_time}
     * has passed today, every open session ends with CLOSING. Floors without a
     * closing time (none is known until the survey) are left alone.
     */
    @Scheduled(fixedDelayString = "${seatsense.closing.check-every:PT1M}")
    public int closeFloorsPastClosingTime() {
        Integer ended = write.execute(status -> {
            LocalTime now = LocalTime.now(time.clock());
            int count = 0;
            for (Floor floor : floors.findAll()) {
                if (floor.getCloseTime() != null && !now.isBefore(floor.getCloseTime())) {
                    for (SeatSession open : sessions.findOpenOnFloor(floor.getId())) {
                        end(open, EndReason.CLOSING);
                        count++;
                    }
                }
            }
            return count;
        });
        if (ended != null && ended > 0) {
            log.info("Closing time: ended {} session(s)", ended);
        }
        return ended == null ? 0 : ended;
    }

    /**
     * Ends every open session (reason ADMIN), for the demo reset and the staff
     * sweep. Returns how many ended.
     */
    public int endAllOpen() {
        Integer ended = write.execute(status -> {
            int count = 0;
            for (Floor floor : floors.findAll()) {
                for (SeatSession open : sessions.findOpenOnFloor(floor.getId())) {
                    end(open, EndReason.ADMIN);
                    count++;
                }
            }
            return count;
        });
        return ended == null ? 0 : ended;
    }

    /**
     * Takes a seat out of service or puts it back (B3.3). Only a FREE seat can go
     * out of service, so staff never remove a seat from under someone.
     *
     * @throws SessionException.NotFound      no such seat
     * @throws InvalidSeatTransitionException the seat is taken, on break or held
     */
    public Seat setOutOfService(String seatCode, boolean outOfService) {
        return write.execute(status -> {
            Seat seat = seats.findByCode(seatCode).orElseThrow(() -> new SessionException.NotFound("seat " + seatCode));
            SeatState current = currentState(seat);
            SeatStateMachine.next(current, outOfService ? SeatEvent.MARK_OUT_OF_SERVICE : SeatEvent.RETURN_TO_SERVICE);
            seat.setOutOfService(outOfService);
            events.publishEvent(new SessionEvents.SeatServiceChanged(seat.getCode(), seat.getFloorId(), outOfService, time.now()));
            return seat;
        });
    }

    private SeatSession end(SeatSession session, EndReason reason) {
        Instant now = time.now();
        session.end(reason, now);
        Seat seat = seats.findById(session.getSeatId()).orElseThrow();
        events.publishEvent(new SeatFreed(seat.getCode(), seat.getFloorId(), reason, now));
        return session;
    }

    /** Breaks the student has used today, across all of today's sessions (library time). */
    public long breaksUsedToday(Long studentId) {
        return read.execute(status -> countBreaksToday(studentId));
    }

    private long countBreaksToday(Long studentId) {
        Instant startOfDay = LocalDate.now(time.clock()).atStartOfDay(time.clock().getZone()).toInstant();
        return sessions.breaksUsedSince(studentId, startOfDay);
    }

    // ---------------------------------------------------------------- reading

    /** The student's open session, if they are inside. The gate uses this to tell an entry from an exit. */
    public Optional<SeatSession> openSessionOf(Long studentId) {
        return read.execute(status -> sessions.findOpenByStudent(studentId));
    }

    public Optional<SeatSession> session(Long sessionId) {
        return read.execute(status -> sessions.findById(sessionId));
    }

    /** Code of the seat a session is on. */
    public String seatCodeOf(SeatSession session) {
        return read.execute(status -> seats.findById(session.getSeatId()).map(Seat::getCode).orElseThrow());
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
        Map<String, SeatState> claimed = new HashMap<>();
        activeHolds().keySet().stream()
            .filter(code -> code.startsWith(floorId + "-"))
            .forEach(code -> claimed.put(code, SeatState.HELD));
        read.execute(status -> {
            sessions.findOpenSeatsOnFloor(floorId)
                .forEach(o -> claimed.put(o.getSeatCode(), o.getStatus().seatState()));
            return null;
        });
        return claimed;
    }

    /** The nearest free seat to {@code seatCode}, using the best-seat ranking with "near" set. */
    Optional<String> alternativeNear(String seatCode) {
        return read.execute(status -> alternativeNearInCurrentTransaction(seatCode));
    }

    private Optional<String> alternativeNearInCurrentTransaction(String seatCode) {
        return rankFreeSeats(SeatPreferences.NONE, seatCode, 1).stream().findFirst();
    }

    private List<String> rankFreeSeats(SeatPreferences preferences, String near, int limit) {
        return ranker.rank(catalog.allCandidates(), currentStates(), catalog.entrances(),
                new RankingRequest(preferences, null, near), limit).stream()
            .map(ScoredSeat::seat)
            .map(SeatCandidate::code)
            .toList();
    }

    private List<List<String>> groupOptions(int size) {
        return groupFinder.find(catalog.allCandidates(), currentStates(), catalog.entrances(),
                RankingRequest.anySeat(), size, GROUP_OPTIONS).stream()
            .map(GroupOption::codes)
            .toList();
    }

    /** Every claimed or out-of-service seat in the library, by code. */
    private Map<String, SeatState> currentStates() {
        Map<String, SeatState> states = new HashMap<>();
        activeHolds().keySet().forEach(code -> states.put(code, SeatState.HELD));
        for (SeatSessionRepository.OpenSeat open : sessions.findAllOpenSeats()) {
            states.put(open.getSeatCode(), open.getStatus().seatState());
        }
        seats.findAll().stream()
            .filter(Seat::isOutOfService)
            .forEach(s -> states.put(s.getCode(), SeatState.OUT_OF_SERVICE));
        return states;
    }

    private SeatState currentState(Seat seat) {
        if (seat.isOutOfService()) {
            return SeatState.OUT_OF_SERVICE;
        }
        return sessions.findOpenBySeat(seat.getId())
            .map(s -> s.getStatus().seatState())
            .orElseGet(() -> activeHolds().containsKey(seat.getCode()) ? SeatState.HELD : SeatState.FREE);
    }

    /** Loads a session with its row locked, so two exits, breaks or returns of one session run one after the other. */
    private Map<String, Long> activeHolds() {
        SeatHoldSource holds = holdSource.getIfAvailable();
        return holds == null ? Map.of() : holds.activeHolds();
    }

    /** The seat held for this student on the waitlist, if any. */
    public Optional<String> heldSeatOf(Long studentId) {
        SeatHoldSource holds = holdSource.getIfAvailable();
        return holds == null ? Optional.empty() : holds.heldSeatOf(studentId);
    }

    private SeatSession find(Long sessionId) {
        return sessions.findByIdForUpdate(sessionId).orElseThrow(() -> new SessionException.NotFound("session " + sessionId));
    }

    private static SeatState seatStateOf(SeatSession session) {
        return session.isOpen() ? session.getStatus().seatState() : SeatState.FREE;
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
