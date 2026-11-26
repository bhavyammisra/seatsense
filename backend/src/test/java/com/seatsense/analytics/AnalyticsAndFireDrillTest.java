package com.seatsense.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.seatsense.PostgresIntegrationTest;
import com.seatsense.analytics.AnalyticsService.Analytics;
import com.seatsense.analytics.AnalyticsService.HeatCell;
import com.seatsense.analytics.FireDrillService.FireDrill;
import com.seatsense.analytics.FireDrillService.FloorList;
import com.seatsense.common.TimeProvider;
import com.seatsense.session.SeatSession;
import com.seatsense.session.SessionService;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Librarian analytics (B4.3) and the fire-drill list (B4.4). Uses sample students SAMPLE0006 and
 * SAMPLE0007 and seats GF-H10 and GF-H11, and removes everything it created afterwards.
 */
@AutoConfigureMockMvc
class AnalyticsAndFireDrillTest extends PostgresIntegrationTest {

    @Autowired
    private AnalyticsService analytics;

    @Autowired
    private FireDrillService fireDrill;

    @Autowired
    private OccupancySnapshots snapshots;

    @Autowired
    private SessionService sessions;

    @Autowired
    private TimeProvider time;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    private ZonedDateTime twoDaysAgoAtTen;

    private long student(int n) {
        return jdbc.queryForObject("select id from student where card_id = ?", Long.class, "SAMPLE%04d".formatted(n));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from seat_session where student_id in (select id from student where card_id in ('SAMPLE0006', 'SAMPLE0007'))");
        if (twoDaysAgoAtTen != null) {
            jdbc.update("delete from occupancy_snapshot where taken_at = ?", java.sql.Timestamp.from(twoDaysAgoAtTen.toInstant()));
        }
        jdbc.update("update seat set out_of_service = false");
        jdbc.update("delete from full_view");
    }

    @Test
    void theHeatmapAveragesAllFloorsForThatWeekdayAndHour() {
        twoDaysAgoAtTen = LocalDate.now(time.clock()).minusDays(2).atTime(10, 15).atZone(time.clock().getZone());
        snapshots.insert("GF", 30, 120, twoDaysAgoAtTen.toInstant(), false);
        snapshots.insert("1F", 90, 180, twoDaysAgoAtTen.toInstant(), false);

        Analytics a = analytics.now();

        HeatCell cell = a.heatmap().stream()
            .filter(c -> c.weekday() == twoDaysAgoAtTen.getDayOfWeek().getValue() && c.hour() == 10)
            .findFirst().orElseThrow();
        assertThat(cell.occupancy()).isCloseTo(120.0 / 300, within(0.0001));
        assertThat(a.daysOfData()).isPositive();
    }

    @Test
    void theMostUsedSeatsCountSessionsAndSayTheyAreSample() {
        for (int n : new int[] {6, 7}) {
            SeatSession s = sessions.takeSeat(student(n), "GF-H10");
            sessions.leave(s.getId());
        }
        sessions.leave(sessions.takeSeat(student(6), "GF-H11").getId());

        Analytics a = analytics.now();

        assertThat(a.topSeats()).filteredOn(s -> s.code().equals("GF-H10")).singleElement()
            .satisfies(s -> assertThat(s.sessions()).isEqualTo(2));
        assertThat(a.topSeats()).extracting(AnalyticsService.SeatUse::code).containsSubsequence("GF-H10", "GF-H11");
        assertThat(a.seatsSample()).isTrue();
    }

    @Test
    void sawFullIsCountedOnlyWhileTheLibraryReallyIsFull() {
        assertThat(analytics.recordFullView()).isFalse();

        jdbc.update("update seat set out_of_service = true");
        assertThat(analytics.recordFullView()).isTrue();
        assertThat(analytics.recordFullView()).isTrue();

        Analytics a = analytics.now();
        assertThat(a.sawFull()).hasSize(7);
        assertThat(a.sawFull().get(6).date()).isEqualTo(LocalDate.now(time.clock()));
        assertThat(a.sawFull().get(6).count()).isEqualTo(2);
        assertThat(a.sawFullTotal()).isEqualTo(2);
    }

    @Test
    void theFireDrillListsPeopleInsideAndOnABreakApart() {
        sessions.takeSeat(student(6), "GF-H10");
        sessions.startBreak(sessions.takeSeat(student(7), "GF-H11").getId());

        FireDrill drill = fireDrill.now();

        FloorList ground = drill.floors().stream().filter(f -> f.id().equals("GF")).findFirst().orElseThrow();
        assertThat(ground.inside()).anySatisfy(p -> {
            assertThat(p.seat()).isEqualTo("GF-H10");
            assertThat(p.cardId()).isEqualTo("SAMPLE0006");
            assertThat(p.sample()).isTrue();
            assertThat(p.breakUntil()).isNull();
        });
        assertThat(ground.onBreak()).anySatisfy(p -> {
            assertThat(p.seat()).isEqualTo("GF-H11");
            assertThat(p.breakUntil()).isNotNull();
        });
        assertThat(ground.inside()).noneMatch(p -> p.seat().equals("GF-H11"));
        assertThat(drill.floors()).hasSize(4);
    }

    @Test
    void namesAreForStaffOnly() throws Exception {
        mvc.perform(get("/api/admin/fire-drill").with(user("l").roles("LIBRARIAN"))).andExpect(status().isOk());
        mvc.perform(get("/api/admin/analytics").with(user("l").roles("LIBRARIAN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.sawFull.length()").value(7));
        mvc.perform(get("/api/admin/fire-drill").with(user("g").roles("GATE"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/analytics").with(user("s").roles("STUDENT"))).andExpect(status().isForbidden());
    }

    @Test
    void anyoneCanReportSeeingFullButItCountsOnlyWhenFull() throws Exception {
        mvc.perform(post("/api/public/saw-full").with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.counted").value(false));
    }
}
