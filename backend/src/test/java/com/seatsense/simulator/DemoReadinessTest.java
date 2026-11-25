package com.seatsense.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.seatsense.PostgresIntegrationTest;
import com.seatsense.common.SeatStateEvent;
import com.seatsense.session.InvalidSeatTransitionException;
import com.seatsense.session.SeatState;
import com.seatsense.session.SessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

/** Out-of-service toggle (B3.3) and the demo simulator (B3.1). */
@AutoConfigureMockMvc
@RecordApplicationEvents
class DemoReadinessTest extends PostgresIntegrationTest {

    @Autowired
    private SessionService sessions;

    @Autowired
    private SimulatorService simulator;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationEvents events;

    @AfterEach
    void cleanUp() {
        simulator.reset();
        jdbc.update("update seat set out_of_service = false where code in ('GF-E1', 'GF-E2')");
    }

    private long student(int n) {
        return jdbc.queryForObject("select id from student where card_id = ?", Long.class, "SAMPLE%04d".formatted(n));
    }

    @Test
    void aFreeSeatGoesOutOfServiceAndBackAndScreensAreTold() {
        sessions.setOutOfService("GF-E1", true);
        assertThat(sessions.claimedSeats("GF")).doesNotContainKey("GF-E1"); // not a claim: a property of the seat
        assertThat(jdbc.queryForObject("select out_of_service from seat where code = 'GF-E1'", Boolean.class)).isTrue();

        sessions.setOutOfService("GF-E1", false);

        assertThat(events.stream(SeatStateEvent.class).filter(e -> e.seatCode().equals("GF-E1")))
            .extracting(SeatStateEvent::newState)
            .containsExactly(SeatState.OUT_OF_SERVICE, SeatState.FREE);
    }

    @Test
    void aSeatSomeoneIsUsingCannotGoOutOfService() {
        sessions.takeSeat(student(1), "GF-E2");

        assertThatThrownBy(() -> sessions.setOutOfService("GF-E2", true))
            .isInstanceOf(InvalidSeatTransitionException.class);
    }

    @Test
    void theAdminEndpointIsForStaffAndAnswers409ForATakenSeat() throws Exception {
        mvc.perform(post("/api/admin/seats/GF-E1/out-of-service").with(user("lib").roles("LIBRARIAN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"outOfService\":true,\"reason\":\"Broken chair\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("GF-E1"))
            .andExpect(jsonPath("$.state").value("OUT_OF_SERVICE"));

        sessions.takeSeat(student(1), "GF-E2");
        mvc.perform(post("/api/admin/seats/GF-E2/out-of-service").with(user("lib").roles("LIBRARIAN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"outOfService\":true}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("INVALID_TRANSITION"));

        mvc.perform(post("/api/admin/seats/GF-E1/out-of-service").with(user("s").roles("STUDENT")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"outOfService\":false}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void anExamEveningFillsEachFloorToItsLevelAndResetEmptiesIt() {
        SimulatorService.RushResult rush = simulator.examEvening(42);

        assertThat(rush.seated()).isGreaterThan(450);
        for (var floor : SimulatorService.EXAM_EVENING.entrySet()) {
            int total = jdbc.queryForObject("select count(*) from seat where floor_id = ?", Integer.class, floor.getKey());
            int open = jdbc.queryForObject("""
                select count(*) from seat_session s join seat on seat.id = s.seat_id
                where seat.floor_id = ? and s.status <> 'ENDED'""", Integer.class, floor.getKey());
            assertThat(open).as("floor %s", floor.getKey()).isEqualTo((int) Math.round(total * floor.getValue()));
        }
        assertThat(sessions.claimedSeats("3F")).hasSize(144);

        int ended = simulator.reset();

        assertThat(ended).isGreaterThanOrEqualTo(rush.seated());
        assertThat(jdbc.queryForObject("select count(*) from seat_session where status <> 'ENDED'", Integer.class)).isZero();
    }
}
