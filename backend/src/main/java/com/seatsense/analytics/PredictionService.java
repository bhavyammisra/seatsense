package com.seatsense.analytics;

import com.seatsense.common.TimeProvider;
import com.seatsense.layout.Floor;
import com.seatsense.layout.FloorRepository;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Seats usually free up around X" (WORKPLAN B4.1, PROJECT.md Section 11.7).
 *
 * <p>For today's weekday, each 15-minute slot gets the average number of free seats seen in that
 * slot on the same weekday over the last {@code seatsense.predictions.weeks} weeks. The answer is
 * the first slot later today where that average reaches {@code seatsense.predictions.min-free}.
 * With no history the answer is empty: nothing is ever made up.
 */
@Service
public class PredictionService {

    static final int SLOT_MINUTES = 15;
    private static final int SLOTS_PER_DAY = 24 * 60 / SLOT_MINUTES;

    /** One floor's prediction. {@code freesUpAround} is null when there is no answer. */
    public record FloorPrediction(String floorId, LocalTime freesUpAround, int weeksOfData, boolean sample) {
    }

    /** {@code minFree} is the number of free seats the prediction looks for. */
    public record Predictions(int minFree, List<FloorPrediction> floors) {
    }

    private final FloorRepository floors;
    private final JdbcTemplate jdbc;
    private final TimeProvider time;
    private final int weeks;
    private final int minFree;

    public PredictionService(FloorRepository floors, JdbcTemplate jdbc, TimeProvider time,
                             @Value("${seatsense.predictions.weeks:4}") int weeks,
                             @Value("${seatsense.predictions.min-free:5}") int minFree) {
        this.floors = floors;
        this.jdbc = jdbc;
        this.time = time;
        this.weeks = weeks;
        this.minFree = minFree;
    }

    @Transactional(readOnly = true)
    public Predictions now() {
        List<FloorPrediction> result = new ArrayList<>();
        for (Floor floor : floors.findAllByOrderByNumberAsc()) {
            result.add(predict(floor));
        }
        return new Predictions(minFree, result);
    }

    FloorPrediction predict(Floor floor) {
        ZoneId zone = time.clock().getZone();
        ZonedDateTime now = ZonedDateTime.now(time.clock());
        DayOfWeek today = now.getDayOfWeek();
        ZonedDateTime startOfToday = now.toLocalDate().atStartOfDay(zone);

        long[] freeSum = new long[SLOTS_PER_DAY];
        int[] count = new int[SLOTS_PER_DAY];
        Set<java.time.LocalDate> days = new HashSet<>();
        boolean[] sample = {false};

        // Only earlier days: today's own numbers are what we are trying to predict.
        jdbc.query("""
                select taken, total, taken_at, is_sample from occupancy_snapshot
                where floor_id = ? and taken_at >= ? and taken_at < ?""",
            rs -> {
                ZonedDateTime at = rs.getTimestamp("taken_at").toInstant().atZone(zone);
                if (at.getDayOfWeek() != today) {
                    return;
                }
                int slot = slotOf(at.toLocalTime());
                freeSum[slot] += rs.getInt("total") - rs.getInt("taken");
                count[slot]++;
                days.add(at.toLocalDate());
                sample[0] |= rs.getBoolean("is_sample");
            },
            floor.getId(), Timestamp.from(startOfToday.minusWeeks(weeks).toInstant()), Timestamp.from(startOfToday.toInstant()));

        if (days.isEmpty()) {
            return new FloorPrediction(floor.getId(), null, 0, false);
        }
        LocalTime close = floor.getCloseTime();
        for (int slot = slotOf(now.toLocalTime()) + 1; slot < SLOTS_PER_DAY; slot++) {
            LocalTime start = LocalTime.of(slot * SLOT_MINUTES / 60, slot * SLOT_MINUTES % 60);
            if (close != null && !start.isBefore(close)) {
                break;
            }
            if (count[slot] > 0 && (double) freeSum[slot] / count[slot] >= minFree) {
                return new FloorPrediction(floor.getId(), start, days.size(), sample[0]);
            }
        }
        return new FloorPrediction(floor.getId(), null, days.size(), sample[0]);
    }

    static int slotOf(LocalTime t) {
        return (t.getHour() * 60 + t.getMinute()) / SLOT_MINUTES;
    }
}
