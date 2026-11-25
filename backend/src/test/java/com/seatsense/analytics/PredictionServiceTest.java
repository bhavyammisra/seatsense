package com.seatsense.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.seatsense.PostgresIntegrationTest;
import com.seatsense.analytics.PredictionService.FloorPrediction;
import com.seatsense.common.TimeProvider;
import com.seatsense.layout.Floor;
import com.seatsense.layout.FloorRepository;
import com.seatsense.simulator.SimulatorService;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Occupancy snapshots and predictions (WORKPLAN B4.1). Uses dates in 2030 so its rows never mix
 * with anything else, and removes them afterwards.
 */
@AutoConfigureMockMvc
class PredictionServiceTest extends PostgresIntegrationTest {

    private static final ZoneId LIBRARY = ZoneId.of("Asia/Kolkata");
    // A Wednesday afternoon. The two Wednesdays before it are the history.
    private static final ZonedDateTime NOW = ZonedDateTime.of(2030, 1, 16, 16, 0, 0, 0, LIBRARY);

    @Autowired
    private FloorRepository floors;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private OccupancySnapshots snapshots;

    @Autowired
    private SimulatorService simulator;

    @Autowired
    private MockMvc mvc;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from occupancy_snapshot where taken_at >= '2029-12-01' or is_sample");
    }

    private PredictionService at(ZonedDateTime now) {
        return new PredictionService(floors, jdbc, new TimeProvider(Clock.fixed(now.toInstant(), LIBRARY)), 4, 5);
    }

    private Floor thirdFloor() {
        return floors.findById("3F").orElseThrow();
    }

    private void snapshot(ZonedDateTime at, int free) {
        snapshots.insert("3F", 144 - free, 144, at.toInstant(), false);
    }

    @Test
    void withNoHistoryThereIsNoPrediction() {
        FloorPrediction p = at(NOW).predict(thirdFloor());

        assertThat(p.freesUpAround()).isNull();
        assertThat(p.weeksOfData()).isZero();
    }

    @Test
    void predictsTheFirstLaterSlotThatUsuallyHasEnoughFreeSeats() {
        for (int weeksAgo = 1; weeksAgo <= 2; weeksAgo++) {
            ZonedDateTime day = NOW.minusWeeks(weeksAgo);
            snapshot(day.withHour(17).withMinute(0), 0);
            snapshot(day.withHour(18).withMinute(5), 3);    // too few
            snapshot(day.withHour(19).withMinute(35), 12);  // enough, in the 19:30 slot
        }
        snapshot(NOW.minusDays(1).withHour(17), 80);      // a Tuesday: other weekdays do not count
        snapshot(NOW.withHour(10), 100);                  // earlier today: not history

        FloorPrediction p = at(NOW).predict(thirdFloor());

        assertThat(p.freesUpAround()).isEqualTo(LocalTime.of(19, 30));
        assertThat(p.weeksOfData()).isEqualTo(2);
        assertThat(p.sample()).isFalse();
    }

    @Test
    void averagesAcrossWeeksBeforeDeciding() {
        snapshot(NOW.minusWeeks(1).withHour(18), 8);
        snapshot(NOW.minusWeeks(2).withHour(18), 0);  // average 4: below 5
        snapshot(NOW.minusWeeks(1).withHour(20), 6);
        snapshot(NOW.minusWeeks(2).withHour(20), 6);

        assertThat(at(NOW).predict(thirdFloor()).freesUpAround()).isEqualTo(LocalTime.of(20, 0));
    }

    @Test
    void onlyLooksAheadOfNow() {
        snapshot(NOW.minusWeeks(1).withHour(11), 50);

        FloorPrediction p = at(NOW).predict(thirdFloor());

        assertThat(p.freesUpAround()).isNull();
        assertThat(p.weeksOfData()).isEqualTo(1);
    }

    @Test
    void theJobStoresOneRowPerFloor() {
        int rows = snapshots.record();

        assertThat(rows).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(distinct floor_id) from occupancy_snapshot where not is_sample "
            + "and taken_at > now() - interval '1 day'", Integer.class)).isEqualTo(4);
        jdbc.update("delete from occupancy_snapshot where not is_sample and taken_at > now() - interval '1 day'");
    }

    @Test
    void sampleHistoryIsMarkedAsSampleAndReplacedNotDoubled() {
        int first = simulator.sampleHistory(1);
        int second = simulator.sampleHistory(2);

        assertThat(second).isEqualTo(first);
        assertThat(jdbc.queryForObject("select count(*) from occupancy_snapshot where is_sample", Integer.class))
            .isEqualTo(first);
        assertThat(jdbc.queryForObject("select count(*) from occupancy_snapshot where is_sample and taken > total", Integer.class))
            .isZero();
    }

    @Test
    void predictionsArePublic() throws Exception {
        mvc.perform(get("/api/public/predictions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.minFree").value(5))
            .andExpect(jsonPath("$.floors.length()").value(4));
    }
}
