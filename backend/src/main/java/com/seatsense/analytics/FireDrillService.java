package com.seatsense.analytics;

import com.seatsense.common.TimeProvider;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emergency headcount (WORKPLAN B4.4): who is in the library right now, floor by floor, so staff
 * can check names off at the assembly point. Students on a break are listed apart: they stepped
 * out through the gate and should not be in the building.
 */
@Service
public class FireDrillService {

    /** One person. {@code since} is when they sat down; {@code breakUntil} is set only for breaks. */
    public record Person(String name, String cardId, boolean sample, String seat, Instant since, Instant breakUntil) {
    }

    public record FloorList(String id, String name, List<Person> inside, List<Person> onBreak) {
    }

    public record FireDrill(OffsetDateTime at, int inside, int onBreak, List<FloorList> floors) {
    }

    private final JdbcTemplate jdbc;
    private final TimeProvider time;

    public FireDrillService(JdbcTemplate jdbc, TimeProvider time) {
        this.jdbc = jdbc;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public FireDrill now() {
        Map<String, FloorList> floors = new LinkedHashMap<>();
        jdbc.query("select id, name from floor order by number",
            rs -> {
                floors.put(rs.getString("id"),
                    new FloorList(rs.getString("id"), rs.getString("name"), new ArrayList<>(), new ArrayList<>()));
            });
        int[] counts = new int[2];
        jdbc.query("""
                select st.floor_id, st.code, s.status, s.started_at, s.break_until,
                       p.name, p.card_id, p.is_sample
                from seat_session s
                join seat st on st.id = s.seat_id
                join student p on p.id = s.student_id
                where s.status in ('ACTIVE', 'ON_BREAK')
                order by st.code""",
            rs -> {
                FloorList floor = floors.get(rs.getString("floor_id"));
                boolean onBreak = "ON_BREAK".equals(rs.getString("status"));
                var breakUntil = rs.getTimestamp("break_until");
                Person person = new Person(rs.getString("name"), rs.getString("card_id"), rs.getBoolean("is_sample"),
                    rs.getString("code"), rs.getTimestamp("started_at").toInstant(),
                    breakUntil == null ? null : breakUntil.toInstant());
                (onBreak ? floor.onBreak() : floor.inside()).add(person);
                counts[onBreak ? 1 : 0]++;
            });
        return new FireDrill(OffsetDateTime.now(time.clock()), counts[0], counts[1], new ArrayList<>(floors.values()));
    }
}
