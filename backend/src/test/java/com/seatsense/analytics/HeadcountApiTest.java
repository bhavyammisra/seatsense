package com.seatsense.analytics;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.seatsense.PostgresIntegrationTest;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** GET /api/admin/headcount. Uses sample students SAMPLE0021 to SAMPLE0025 and removes its sessions afterwards. */
@AutoConfigureMockMvc
class HeadcountApiTest extends PostgresIntegrationTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-26T18:00:00+05:30");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void removeTestSessions() {
        jdbc.update("""
            delete from seat_session where student_id in (
              select id from student where card_id between 'SAMPLE0021' and 'SAMPLE0025')""");
    }

    private void open(int student, String seatCode, String status) {
        jdbc.update("""
            insert into seat_session (student_id, seat_id, status, started_at, break_until)
            values ((select id from student where card_id = ?), (select id from seat where code = ?), ?, ?, ?)""",
            "SAMPLE%04d".formatted(student), seatCode, status, NOW,
            status.equals("ON_BREAK") ? NOW.plusMinutes(30) : null);
    }

    private void ended(int student, String seatCode) {
        jdbc.update("""
            insert into seat_session (student_id, seat_id, status, started_at, ended_at, end_reason)
            values ((select id from student where card_id = ?), (select id from seat where code = ?),
                    'ENDED', ?, ?, 'LEAVE')""",
            "SAMPLE%04d".formatted(student), seatCode, NOW.minusHours(2), NOW.minusHours(1));
    }

    @Test
    void countsPeopleSeatedAndOnBreakPerFloor() throws Exception {
        open(21, "GF-A1", "ACTIVE");
        open(22, "GF-A2", "ACTIVE");
        open(23, "1F-A1", "ACTIVE");
        open(24, "1F-A2", "ON_BREAK");
        ended(25, "2F-A1");

        mvc.perform(get("/api/admin/headcount").with(user("lib").roles("LIBRARIAN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.inside").value(3))
            .andExpect(jsonPath("$.onBreak").value(1))
            .andExpect(jsonPath("$.floors.length()").value(4))
            .andExpect(jsonPath("$.floors[0].id").value("GF"))
            .andExpect(jsonPath("$.floors[0].inside").value(2))
            .andExpect(jsonPath("$.floors[1].id").value("1F"))
            .andExpect(jsonPath("$.floors[1].inside").value(1))
            .andExpect(jsonPath("$.floors[2].inside").value(0))
            .andExpect(jsonPath("$.floors[3].id").value("3F"));
    }

    @Test
    void anEmptyLibraryListsEveryFloorAtZero() throws Exception {
        mvc.perform(get("/api/admin/headcount").with(user("lib").roles("LIBRARIAN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.floors.length()").value(4))
            .andExpect(jsonPath("$.floors[*].inside").isArray());
    }

    @Test
    void adminCanSeeItToo() throws Exception {
        mvc.perform(get("/api/admin/headcount").with(user("boss").roles("ADMIN"))).andExpect(status().isOk());
    }

    @Test
    void studentsAndTheGateCannot() throws Exception {
        mvc.perform(get("/api/admin/headcount").with(user("s").roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/headcount").with(user("g").roles("GATE"))).andExpect(status().isForbidden());
    }

    @Test
    void withoutLoginItIs401() throws Exception {
        mvc.perform(get("/api/admin/headcount"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("NOT_LOGGED_IN"));
    }
}
