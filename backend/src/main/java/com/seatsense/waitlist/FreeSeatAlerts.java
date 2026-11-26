package com.seatsense.waitlist;

import com.seatsense.analytics.Availability.FloorAvailability;
import com.seatsense.analytics.AvailabilityService;
import com.seatsense.auth.AppUserRepository;
import com.seatsense.common.SeatStateEvent;
import com.seatsense.common.TimeProvider;
import com.seatsense.session.SeatState;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * "Tell me when Floor N has 5+ free seats" (WORKPLAN B4.2). A student sets an alert from the
 * portal; the first time that floor has at least that many free seats, they get one FLOOR_FREE
 * notification on /user/queue/notifications and the alert is done.
 */
@Service
public class FreeSeatAlerts {

    public record Alert(long id, String floorId, int minFree) {
    }

    static final int MAX_MIN_FREE = 50;

    private final JdbcTemplate jdbc;
    private final AvailabilityService availability;
    private final AppUserRepository users;
    private final SimpMessagingTemplate broker;
    private final TimeProvider time;

    public FreeSeatAlerts(JdbcTemplate jdbc, AvailabilityService availability, AppUserRepository users,
                          SimpMessagingTemplate broker, TimeProvider time) {
        this.jdbc = jdbc;
        this.availability = availability;
        this.users = users;
        this.broker = broker;
        this.time = time;
    }

    public List<Alert> activeFor(long studentId) {
        return jdbc.query("select id, floor_id, min_free from free_seat_alert where student_id = ? and status = 'ACTIVE' order by floor_id",
            (rs, i) -> new Alert(rs.getLong("id"), rs.getString("floor_id"), rs.getInt("min_free")), studentId);
    }

    /**
     * Sets the student's alert for a floor (asking again changes the number).
     *
     * @throws WaitlistException NOT_FOUND for an unknown floor, ALREADY_FREE if the floor already has that many
     *                           free seats, VALIDATION_FAILED for a number outside 1 to 50
     */
    public Alert set(long studentId, String floorId, int minFree) {
        if (minFree < 1 || minFree > MAX_MIN_FREE) {
            throw WaitlistException.badAlertNumber(MAX_MIN_FREE);
        }
        FloorAvailability floor = floors().get(floorId);
        if (floor == null) {
            throw WaitlistException.noSuchFloor(floorId);
        }
        if (floor.free() >= minFree) {
            throw WaitlistException.alreadyFree(floor.name(), floor.free());
        }
        Long id = jdbc.queryForObject("""
                insert into free_seat_alert (student_id, floor_id, min_free, status, created_at)
                values (?, ?, ?, 'ACTIVE', ?)
                on conflict (student_id, floor_id) where status = 'ACTIVE' do update set min_free = excluded.min_free
                returning id""",
            Long.class, studentId, floorId, minFree, Timestamp.from(time.now()));
        return new Alert(id, floorId, minFree);
    }

    public void cancel(long studentId, long alertId) {
        jdbc.update("update free_seat_alert set status = 'CANCELLED', ended_at = ? where id = ? and student_id = ? and status = 'ACTIVE'",
            Timestamp.from(time.now()), alertId, studentId);
    }

    /** A seat became free (after the change is saved): send any alert whose floor now has enough free seats. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSeatChange(SeatStateEvent event) {
        if (event.newState() == SeatState.FREE) {
            sendDue();
        }
    }

    /** Sends every due alert once. Returns how many were sent. */
    public int sendDue() {
        record Due(long id, long studentId, String floorId, int minFree) {
        }
        List<Due> active = jdbc.query("select id, student_id, floor_id, min_free from free_seat_alert where status = 'ACTIVE'",
            (rs, i) -> new Due(rs.getLong("id"), rs.getLong("student_id"), rs.getString("floor_id"), rs.getInt("min_free")));
        if (active.isEmpty()) {
            return 0; // the usual case: no work, and no counting seats
        }
        Map<String, FloorAvailability> floors = floors();
        int sent = 0;
        for (Due alert : active) {
            FloorAvailability floor = floors.get(alert.floorId());
            if (floor == null || floor.free() < alert.minFree()) {
                continue;
            }
            // Only the update that flips ACTIVE to SENT notifies, so two seat changes at once send it once.
            int won = jdbc.update("update free_seat_alert set status = 'SENT', ended_at = ? where id = ? and status = 'ACTIVE'",
                Timestamp.from(time.now()), alert.id());
            if (won == 1) {
                sent++;
                users.findByStudentId(alert.studentId()).ifPresent(user -> broker.convertAndSendToUser(
                    user.getUsername(), "/queue/notifications",
                    Map.of("type", "FLOOR_FREE", "floor", floor.id(), "floorName", floor.name(), "free", floor.free())));
            }
        }
        return sent;
    }

    private Map<String, FloorAvailability> floors() {
        return availability.now().floors().stream().collect(Collectors.toMap(FloorAvailability::id, Function.identity()));
    }
}
