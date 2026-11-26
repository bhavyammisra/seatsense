package com.seatsense.simulator;

import com.seatsense.common.TimeProvider;
import com.seatsense.session.SessionException;
import com.seatsense.session.SessionService;
import com.seatsense.waitlist.WaitlistService;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Demo simulator (WORKPLAN B3.1): fills the library like an exam-season evening,
 * fast-forwards the library clock, and resets everything. It goes through the
 * real session service, so every screen updates live as seats fill.
 *
 * <p>Simulated students are created as needed with card ids {@code SIM-0001}
 * upwards and {@code is_sample = true}. They are never real people.
 */
@Service
public class SimulatorService {

    /** How full each floor gets in the exam-evening scenario. The top floor is full. */
    static final Map<String, Double> EXAM_EVENING = Map.of("GF", 0.80, "1F", 0.93, "2F", 0.88, "3F", 1.00);

    private static final Logger log = LoggerFactory.getLogger(SimulatorService.class);

    private final SessionService sessions;
    private final WaitlistService waitlist;
    private final JdbcTemplate jdbc;
    private final TimeProvider time;

    public SimulatorService(SessionService sessions, WaitlistService waitlist, JdbcTemplate jdbc, TimeProvider time) {
        this.sessions = sessions;
        this.waitlist = waitlist;
        this.jdbc = jdbc;
        this.time = time;
    }

    public record RushResult(int seated, int skipped) {
    }

    /** Fills free seats floor by floor to the scenario's level. Seats already taken are left alone. */
    public RushResult examEvening(long seed) {
        Random random = new Random(seed);
        int seated = 0;
        int skipped = 0;
        for (String floor : List.of("GF", "1F", "2F", "3F")) {
            List<String> free = new ArrayList<>(jdbc.queryForList("""
                select s.code from seat s
                where s.floor_id = ? and not s.out_of_service
                  and not exists (select 1 from seat_session ss where ss.seat_id = s.id and ss.status <> 'ENDED')
                  and not exists (select 1 from seat_hold h where h.seat_id = s.id and h.status = 'ACTIVE')
                order by s.code""", String.class, floor));
            int total = jdbc.queryForObject("select count(*) from seat where floor_id = ?", Integer.class, floor);
            int taken = total - free.size();
            int wanted = (int) Math.round(total * EXAM_EVENING.get(floor)) - taken;
            Collections.shuffle(free, random);
            for (String code : free.subList(0, Math.max(0, Math.min(wanted, free.size())))) {
                try {
                    sessions.takeSeat(nextFreeSimStudent(), code);
                    seated++;
                } catch (SessionException race) {
                    skipped++; // someone real took it in the meantime
                }
            }
        }
        log.info("Simulator: exam evening seated {} students ({} skipped)", seated, skipped);
        return new RushResult(seated, skipped);
    }

    /** Shape of an exam-season day, as a share of each floor's evening peak: (hour, share). Invented, SAMPLE only. */
    private static final double[][] SAMPLE_DAY = {
        {8, 0.15}, {10, 0.50}, {12, 0.78}, {13, 0.66}, {14, 0.88}, {17, 1.00}, {19, 0.97}, {20, 0.70}, {21, 0.45}, {22, 0.20},
    };

    /**
     * Replaces the SAMPLE occupancy history with 4 invented weeks, one row per floor every 15 minutes
     * from 08:00 to 22:00 (B4.1). Every row is marked is_sample, and screens say "sample data".
     * Real snapshots are never touched. Returns how many rows were written.
     */
    @Transactional
    public int sampleHistory(long seed) {
        Random random = new Random(seed);
        jdbc.update("delete from occupancy_snapshot where is_sample");
        Map<String, Integer> totals = new java.util.HashMap<>();
        jdbc.query("select floor_id, count(*) as n from seat group by floor_id",
            rs -> { totals.put(rs.getString("floor_id"), rs.getInt("n")); });
        LocalDate today = LocalDate.now(time.clock());
        List<Object[]> rows = new ArrayList<>();
        for (int daysAgo = 28; daysAgo >= 1; daysAgo--) {
            LocalDate day = today.minusDays(daysAgo);
            boolean weekend = day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY;
            for (LocalTime t = LocalTime.of(8, 0); !t.isAfter(LocalTime.of(22, 0)); t = t.plusMinutes(15)) {
                ZonedDateTime at = day.atTime(t).atZone(time.clock().getZone());
                for (Map.Entry<String, Double> floor : EXAM_EVENING.entrySet()) {
                    int total = totals.getOrDefault(floor.getKey(), 0);
                    double share = shareAt(t) * floor.getValue() * (weekend ? 0.6 : 1.0) + (random.nextDouble() - 0.5) * 0.06;
                    int taken = (int) Math.round(total * Math.max(0, Math.min(1, share)));
                    rows.add(new Object[] {floor.getKey(), taken, total, Timestamp.from(at.toInstant())});
                }
            }
        }
        jdbc.batchUpdate("insert into occupancy_snapshot (floor_id, taken, total, taken_at, is_sample) values (?, ?, ?, ?, true)", rows);
        log.info("Simulator: wrote {} SAMPLE occupancy snapshots", rows.size());
        return rows.size();
    }

    private static double shareAt(LocalTime t) {
        double hour = t.getHour() + t.getMinute() / 60.0;
        for (int i = 1; i < SAMPLE_DAY.length; i++) {
            if (hour <= SAMPLE_DAY[i][0]) {
                double[] a = SAMPLE_DAY[i - 1];
                double[] b = SAMPLE_DAY[i];
                return a[1] + (b[1] - a[1]) * (hour - a[0]) / (b[0] - a[0]);
            }
        }
        return SAMPLE_DAY[SAMPLE_DAY.length - 1][1];
    }

    /** Moves the library clock forward, so breaks and offers expire on stage. */
    public void advance(int minutes) {
        if (minutes <= 0 || minutes > 24 * 60) {
            throw new IllegalArgumentException("advanceMinutes must be between 1 and 1440");
        }
        time.advance(Duration.ofMinutes(minutes));
        sessions.expireBreaks();
        waitlist.expireHolds();
    }

    /** Ends every open session and clears the waitlist. Returns how many sessions ended. */
    public int reset() {
        waitlist.clearAll();
        return sessions.endAllOpen();
    }

    /** A simulated student who is not inside, created if every existing one is. */
    private Long nextFreeSimStudent() {
        List<Long> free = jdbc.queryForList("""
            select st.id from student st
            where st.card_id like 'SIM-%'
              and not exists (select 1 from seat_session ss where ss.student_id = st.id and ss.status <> 'ENDED')
            order by st.id limit 1""", Long.class);
        if (!free.isEmpty()) {
            return free.get(0);
        }
        Integer count = jdbc.queryForObject("select count(*) from student where card_id like 'SIM-%'", Integer.class);
        int n = (count == null ? 0 : count) + 1;
        return jdbc.queryForObject(
            "insert into student (card_id, name, is_sample) values (?, ?, true) returning id",
            Long.class, "SIM-%04d".formatted(n), "Simulated student %d".formatted(n));
    }
}
