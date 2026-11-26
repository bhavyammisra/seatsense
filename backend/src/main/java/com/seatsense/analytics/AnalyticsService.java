package com.seatsense.analytics;

import com.seatsense.common.TimeProvider;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Librarian analytics (WORKPLAN B4.3): the busiest hours, the most-used seats, and how often the
 * public site showed the library as FULL. Everything is counted from stored rows; when there are no
 * rows the answer is empty, never a made-up number.
 */
@Service
public class AnalyticsService {

    static final int HEATMAP_WEEKS = 4;
    static final int TOP_SEATS_DAYS = 30;
    static final int TOP_SEATS = 10;
    static final int FULL_VIEW_DAYS = 7;

    /** Average share of seats not free, for one weekday (1 = Monday) and hour, across all floors. */
    public record HeatCell(int weekday, int hour, double occupancy) {
    }

    public record SeatUse(String code, int sessions, long minutes) {
    }

    public record DayCount(LocalDate date, int count) {
    }

    /**
     * @param daysOfData  distinct days behind the heatmap
     * @param sample      true when any heatmap row is simulator SAMPLE data
     * @param seatsSample true when any counted session belongs to a SAMPLE student
     */
    public record Analytics(List<HeatCell> heatmap, int daysOfData, boolean sample,
                            List<SeatUse> topSeats, boolean seatsSample,
                            List<DayCount> sawFull, int sawFullTotal) {
    }

    private final JdbcTemplate jdbc;
    private final AvailabilityService availability;
    private final TimeProvider time;

    public AnalyticsService(JdbcTemplate jdbc, AvailabilityService availability, TimeProvider time) {
        this.jdbc = jdbc;
        this.availability = availability;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public Analytics now() {
        Instant now = time.now();
        String zone = time.clock().getZone().getId();

        List<HeatCell> heatmap = new ArrayList<>();
        boolean[] sample = {false};
        jdbc.query("""
                select extract(isodow from taken_at at time zone ?)::int as dow,
                       extract(hour from taken_at at time zone ?)::int as hr,
                       sum(taken)::float / nullif(sum(total), 0) as occupancy,
                       bool_or(is_sample) as sample
                from occupancy_snapshot
                where taken_at >= ?
                group by 1, 2
                order by 1, 2""",
            rs -> {
                heatmap.add(new HeatCell(rs.getInt("dow"), rs.getInt("hr"), rs.getDouble("occupancy")));
                sample[0] |= rs.getBoolean("sample");
            },
            zone, zone, Timestamp.from(now.minus(Duration.ofDays(7L * HEATMAP_WEEKS))));
        Integer days = jdbc.queryForObject("""
                select count(distinct (taken_at at time zone ?)::date) from occupancy_snapshot where taken_at >= ?""",
            Integer.class, zone, Timestamp.from(now.minus(Duration.ofDays(7L * HEATMAP_WEEKS))));

        List<SeatUse> topSeats = jdbc.query("""
                select st.code, count(*) as sessions,
                       sum(extract(epoch from (coalesce(s.ended_at, ?) - s.started_at)))::bigint / 60 as minutes
                from seat_session s join seat st on st.id = s.seat_id
                where s.started_at >= ?
                group by st.code
                order by sessions desc, minutes desc, st.code
                limit ?""",
            (rs, i) -> new SeatUse(rs.getString("code"), rs.getInt("sessions"), rs.getLong("minutes")),
            Timestamp.from(now), Timestamp.from(now.minus(Duration.ofDays(TOP_SEATS_DAYS))), TOP_SEATS);
        Boolean seatsSample = jdbc.queryForObject("""
                select coalesce(bool_or(st.is_sample), false) from seat_session s join student st on st.id = s.student_id
                where s.started_at >= ?""",
            Boolean.class, Timestamp.from(now.minus(Duration.ofDays(TOP_SEATS_DAYS))));

        LocalDate today = LocalDate.now(time.clock());
        LocalDate first = today.minusDays(FULL_VIEW_DAYS - 1L);
        List<DayCount> sawFull = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
            sawFull.add(new DayCount(d, 0));
        }
        jdbc.query("""
                select (seen_at at time zone ?)::date as day, count(*) as n
                from full_view where seen_at >= ? group by 1""",
            rs -> {
                LocalDate day = rs.getDate("day").toLocalDate();
                int index = (int) (day.toEpochDay() - first.toEpochDay());
                if (index >= 0 && index < sawFull.size()) {
                    sawFull.set(index, new DayCount(day, rs.getInt("n")));
                }
            },
            zone, Timestamp.from(first.atStartOfDay(time.clock().getZone()).toInstant()));
        int total = sawFull.stream().mapToInt(DayCount::count).sum();

        return new Analytics(heatmap, days == null ? 0 : days, sample[0], topSeats, Boolean.TRUE.equals(seatsSample),
            sawFull, total);
    }

    /**
     * Someone is looking at the public site while it says FULL. Stored only if the library really
     * is full right now, so the count cannot be inflated by calling this at other times.
     */
    public boolean recordFullView() {
        if (!availability.now().full()) {
            return false;
        }
        jdbc.update("insert into full_view (seen_at) values (?)", Timestamp.from(time.now()));
        return true;
    }
}
