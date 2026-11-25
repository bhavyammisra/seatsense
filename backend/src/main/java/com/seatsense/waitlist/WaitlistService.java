package com.seatsense.waitlist;

import com.seatsense.common.TimeProvider;
import com.seatsense.layout.FloorRepository;
import com.seatsense.layout.Seat;
import com.seatsense.layout.SeatRepository;
import com.seatsense.session.SeatHoldSource;
import com.seatsense.session.SessionEvents.SeatFreed;
import com.seatsense.session.SessionEvents.SeatTaken;
import com.seatsense.session.SessionService;
import com.seatsense.waitlist.WaitlistEvents.HoldReleased;
import com.seatsense.waitlist.WaitlistEvents.Notify;
import com.seatsense.waitlist.WaitlistEvents.SeatHeld;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The waitlist (WORKPLAN B2.2, PROJECT.md Section 11.5): first come, first served.
 *
 * <ul>
 *   <li>When a seat frees, the oldest WAITING student who wants that floor (or any
 *       floor) gets it HELD for 15 minutes and a SEAT_OFFERED notification.</li>
 *   <li>Accepting restarts the hold at a fresh 15 minutes; an offer can be accepted once.</li>
 *   <li>The hold ends USED when that student sits down. If it runs out first, the
 *       seat goes to the next person in the queue.</li>
 *   <li>Leaving the queue releases any hold, which also goes to the next person.</li>
 * </ul>
 * Implements {@link SeatHoldSource}, so held seats show as HELD on every map and
 * only the holder can take them.
 */
@Service
public class WaitlistService implements SeatHoldSource {

    private static final Logger log = LoggerFactory.getLogger(WaitlistService.class);

    private final WaitlistEntryRepository entries;
    private final SeatHoldRepository holds;
    private final SeatRepository seats;
    private final FloorRepository floors;
    private final SessionService sessions;
    private final ApplicationEventPublisher events;
    private final TimeProvider time;
    private final Duration holdLength;

    public WaitlistService(
        WaitlistEntryRepository entries,
        SeatHoldRepository holds,
        SeatRepository seats,
        FloorRepository floors,
        SessionService sessions,
        ApplicationEventPublisher events,
        TimeProvider time,
        @Value("${seatsense.waitlist.hold-length:PT15M}") Duration holdLength) {
        this.entries = entries;
        this.holds = holds;
        this.seats = seats;
        this.floors = floors;
        this.sessions = sessions;
        this.events = events;
        this.time = time;
        this.holdLength = holdLength;
    }

    /** What a student sees about their place (docs/api.md, GET /api/waitlist/me). */
    public record Place(String status, Integer position, String offeredSeat, Instant offerExpiresAt) {

        static final Place NONE = new Place("NONE", null, null, null);
    }

    // ---------------------------------------------------------------- student actions

    @Transactional
    public Place join(Long studentId, String floorPref) {
        if (sessions.openSessionOf(studentId).isPresent()) {
            throw WaitlistException.alreadyInside();
        }
        if (entries.findLive(studentId).isPresent()) {
            throw WaitlistException.alreadyWaiting();
        }
        String floor = floorPref == null || floorPref.isBlank() ? null : floorPref;
        if (floor != null && !floors.existsById(floor)) {
            throw WaitlistException.noSuchFloor(floor);
        }
        entries.saveAndFlush(new WaitlistEntry(studentId, floor, time.now()));
        return placeOf(studentId);
    }

    @Transactional(readOnly = true)
    public Place placeOf(Long studentId) {
        return entries.findLive(studentId).map(this::place).orElse(Place.NONE);
    }

    @Transactional
    public void leave(Long studentId) {
        entries.findLive(studentId).ifPresent(entry -> {
            entry.end(WaitlistStatus.CANCELLED, time.now());
            holds.findByStudentIdAndStatus(studentId, SeatHold.ACTIVE)
                .ifPresent(hold -> releaseAndPassOn(hold, "CANCELLED"));
        });
    }

    /** Accept the current offer: the hold restarts at a fresh 15 minutes. Only once. */
    @Transactional
    public Place accept(Long studentId) {
        WaitlistEntry entry = entries.findLive(studentId)
            .filter(e -> e.getStatus() == WaitlistStatus.OFFERED)
            .orElseThrow(WaitlistException::noOffer);
        SeatHold hold = holds.findByStudentIdAndStatus(studentId, SeatHold.ACTIVE)
            .orElseThrow(WaitlistException::noOffer);
        Instant until = time.now().plus(holdLength);
        entry.accept(until);
        hold.extendTo(until);
        return place(entry);
    }

    /** Cancels every live place and active hold without offering seats on (demo reset). */
    @Transactional
    public void clearAll() {
        Instant now = time.now();
        holds.findByStatus(SeatHold.ACTIVE).forEach(hold -> {
            hold.close("CANCELLED");
            Seat seat = seats.findById(hold.getSeatId()).orElseThrow();
            events.publishEvent(new HoldReleased(seat.getCode(), seat.getFloorId(), "CANCELLED", now));
        });
        entries.findAll().stream()
            .filter(e -> e.getStatus().isLive())
            .forEach(e -> e.end(WaitlistStatus.CANCELLED, now));
    }

    // ---------------------------------------------------------------- reacting to seats

    /** A seat became free: offer it to the next person waiting. Runs after the freeing transaction commits. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSeatFreed(SeatFreed freed) {
        seats.findByCode(freed.seatCode()).ifPresent(this::offerToNext);
    }

    /**
     * A student sat down. Runs inside the same transaction, so the hold is closed
     * together with the new session: USED if they took the held seat; if they took
     * another one, the held seat is released to the next person.
     */
    @EventListener
    public void onSeatTaken(SeatTaken taken) {
        Instant now = time.now();
        holds.findByStudentIdAndStatus(taken.studentId(), SeatHold.ACTIVE).ifPresent(hold -> {
            Seat heldSeat = seats.findById(hold.getSeatId()).orElseThrow();
            if (heldSeat.getCode().equals(taken.seatCode())) {
                hold.close("USED");
            } else {
                releaseAndPassOn(hold, "CANCELLED");
            }
        });
        entries.findLive(taken.studentId()).ifPresent(entry -> entry.end(WaitlistStatus.DONE, now));
    }

    /** Offers that ran out go to the next person (every 30 seconds). Returns how many expired. */
    @Scheduled(fixedDelayString = "${seatsense.waitlist.check-every:PT30S}")
    @Transactional
    public int expireHolds() {
        List<SeatHold> overdue = holds.findByStatusAndExpiresAtBefore(SeatHold.ACTIVE, time.now());
        for (SeatHold hold : overdue) {
            entries.findLive(hold.getStudentId()).ifPresent(entry -> entry.end(WaitlistStatus.EXPIRED, time.now()));
            Seat seat = seats.findById(hold.getSeatId()).orElseThrow();
            events.publishEvent(new Notify(hold.getStudentId(), Map.of("type", "OFFER_EXPIRED", "seat", seat.getCode())));
            releaseAndPassOn(hold, "EXPIRED");
        }
        if (!overdue.isEmpty()) {
            log.info("{} seat offer(s) ran out and moved on", overdue.size());
        }
        return overdue.size();
    }

    private void releaseAndPassOn(SeatHold hold, String reason) {
        hold.close(reason);
        holds.flush();
        Seat seat = seats.findById(hold.getSeatId()).orElseThrow();
        events.publishEvent(new HoldReleased(seat.getCode(), seat.getFloorId(), reason, time.now()));
        offerToNext(seat);
    }

    /** Holds the seat for the oldest WAITING student who wants its floor, if the seat is really free. */
    void offerToNext(Seat seat) {
        if (seat.isOutOfService()
            || holds.findBySeatIdAndStatus(seat.getId(), SeatHold.ACTIVE).isPresent()
            || sessions.claimedSeats(seat.getFloorId()).containsKey(seat.getCode())) {
            return;
        }
        List<WaitlistEntry> queue = entries.queueFor(seat.getFloorId());
        if (queue.isEmpty()) {
            return;
        }
        WaitlistEntry next = queue.get(0);
        Instant now = time.now();
        Instant until = now.plus(holdLength);
        holds.saveAndFlush(new SeatHold(seat.getId(), next.getStudentId(), now, until));
        next.offer(seat.getId(), until);
        events.publishEvent(new SeatHeld(seat.getCode(), seat.getFloorId(), next.getStudentId(), until, now));
        events.publishEvent(new Notify(next.getStudentId(),
            Map.of("type", "SEAT_OFFERED", "seat", seat.getCode(), "expiresAt", until.toString())));
    }

    // ---------------------------------------------------------------- SeatHoldSource

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> activeHolds() {
        List<SeatHold> active = holds.findByStatus(SeatHold.ACTIVE);
        if (active.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> codeById = seats.findAllById(active.stream().map(SeatHold::getSeatId).toList()).stream()
            .collect(Collectors.toMap(Seat::getId, Seat::getCode));
        Map<String, Long> result = new HashMap<>();
        active.forEach(h -> result.put(codeById.get(h.getSeatId()), h.getStudentId()));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> heldSeatOf(Long studentId) {
        return holds.findByStudentIdAndStatus(studentId, SeatHold.ACTIVE)
            .flatMap(h -> seats.findById(h.getSeatId()))
            .map(Seat::getCode);
    }

    private Place place(WaitlistEntry entry) {
        Integer position = entry.getStatus() == WaitlistStatus.WAITING
            ? (int) entries.countAheadOf(entry.getJoinedAt()) + 1
            : null;
        String seat = entry.getOfferedSeatId() == null ? null
            : seats.findById(entry.getOfferedSeatId()).map(Seat::getCode).orElse(null);
        return new Place(entry.getStatus().name(), position, seat, entry.getOfferExpiresAt());
    }

}
