package com.seatsense.analytics;

import com.seatsense.analytics.Availability.FloorAvailability;
import com.seatsense.common.TimeProvider;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores how full each floor is, every 5 minutes (WORKPLAN B4.1, table {@code occupancy_snapshot}).
 * Predictions and the busiest-hours heatmap are built from these rows.
 */
@Service
public class OccupancySnapshots {

    private final AvailabilityService availability;
    private final JdbcTemplate jdbc;
    private final TimeProvider time;

    public OccupancySnapshots(AvailabilityService availability, JdbcTemplate jdbc, TimeProvider time) {
        this.availability = availability;
        this.jdbc = jdbc;
        this.time = time;
    }

    /** Saves one row per floor with the seats that are not free right now. Returns how many rows. */
    @Scheduled(fixedDelayString = "${seatsense.snapshots.every:PT5M}",
        initialDelayString = "${seatsense.snapshots.every:PT5M}")
    @Transactional
    public int record() {
        Instant at = time.now();
        int rows = 0;
        for (FloorAvailability floor : availability.now().floors()) {
            insert(floor.id(), floor.total() - floor.free(), floor.total(), at, false);
            rows++;
        }
        return rows;
    }

    /** One snapshot row. {@code sample} marks rows the demo simulator invents. */
    public void insert(String floorId, int taken, int total, Instant at, boolean sample) {
        jdbc.update("insert into occupancy_snapshot (floor_id, taken, total, taken_at, is_sample) values (?, ?, ?, ?, ?)",
            floorId, taken, total, Timestamp.from(at), sample);
    }
}
