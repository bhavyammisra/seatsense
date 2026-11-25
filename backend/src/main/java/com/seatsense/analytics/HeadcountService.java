package com.seatsense.analytics;

import com.seatsense.analytics.Headcount.FloorHeadcount;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Counts open sessions for the librarian (WORKPLAN B1.6). A reporting query, so
 * it reads the tables directly in one statement instead of loading sessions.
 */
@Service
public class HeadcountService {

    // Every floor appears, even with nobody on it (left joins).
    private static final String PER_FLOOR = """
        select f.id,
               count(s.id) filter (where s.status = 'ACTIVE')   as inside,
               count(s.id) filter (where s.status = 'ON_BREAK') as on_break
        from floor f
        left join seat st on st.floor_id = f.id
        left join seat_session s on s.seat_id = st.id and s.status in ('ACTIVE', 'ON_BREAK')
        group by f.id, f.number
        order by f.number""";

    private final JdbcTemplate jdbc;

    public HeadcountService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Headcount now() {
        List<FloorHeadcount> floors = new ArrayList<>();
        int[] totals = new int[2];
        jdbc.query(PER_FLOOR, rs -> {
            int inside = rs.getInt("inside");
            totals[0] += inside;
            totals[1] += rs.getInt("on_break");
            floors.add(new FloorHeadcount(rs.getString("id"), inside));
        });
        return new Headcount(totals[0], floors, totals[1]);
    }
}
