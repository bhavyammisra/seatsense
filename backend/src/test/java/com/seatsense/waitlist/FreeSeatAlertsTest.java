package com.seatsense.waitlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.seatsense.PostgresIntegrationTest;
import com.seatsense.auth.AppUser;
import com.seatsense.auth.AppUserRepository;
import com.seatsense.auth.Role;
import com.seatsense.common.SeatStateEvent;
import com.seatsense.session.SeatState;
import com.seatsense.waitlist.FreeSeatAlerts.Alert;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * "Tell me when Floor N has 5+ free seats" (WORKPLAN B4.2). Uses sample students SAMPLE0003 to
 * SAMPLE0005 and makes floor 2F short of seats by taking most of them out of service; puts
 * everything back afterwards.
 */
@AutoConfigureMockMvc
class FreeSeatAlertsTest extends PostgresIntegrationTest {

    @Autowired
    private FreeSeatAlerts alerts;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private MockMvc mvc;

    @MockitoSpyBean
    private SimpMessagingTemplate broker;

    private long student(int n) {
        return jdbc.queryForObject("select id from student where card_id = ?", Long.class, "SAMPLE%04d".formatted(n));
    }

    /** Leaves exactly {@code free} seats in service on 2F. */
    private void secondFloorWith(int free) {
        jdbc.update("update seat set out_of_service = false where floor_id = '2F'");
        jdbc.update("""
            update seat set out_of_service = true where floor_id = '2F'
            and code not in (select code from seat where floor_id = '2F' order by code limit ?)""", free);
    }

    @BeforeEach
    void setUp() {
        if (!users.existsByUsername("al.student")) {
            users.save(new AppUser("al.student", "{noop}unused", Role.STUDENT, student(3)));
        }
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("update seat set out_of_service = false where floor_id = '2F'");
        jdbc.update("delete from free_seat_alert where student_id in (select id from student where card_id between 'SAMPLE0003' and 'SAMPLE0005')");
        jdbc.update("delete from app_user where username = 'al.student'");
    }

    @Test
    void aFloorThatAlreadyHasEnoughSeatsNeedsNoAlert() {
        assertThatThrownBy(() -> alerts.set(student(3), "1F", 5))
            .isInstanceOfSatisfying(WaitlistException.class, e -> assertThat(e.errorCode()).isEqualTo("ALREADY_FREE"));
    }

    @Test
    void badNumbersAndUnknownFloorsAreRefused() {
        assertThatThrownBy(() -> alerts.set(student(3), "2F", 0))
            .isInstanceOfSatisfying(WaitlistException.class, e -> assertThat(e.errorCode()).isEqualTo("VALIDATION_FAILED"));
        assertThatThrownBy(() -> alerts.set(student(3), "9F", 5))
            .isInstanceOfSatisfying(WaitlistException.class, e -> assertThat(e.errorCode()).isEqualTo("NOT_FOUND"));
    }

    @Test
    void askingAgainChangesTheNumberInsteadOfAddingAnAlert() {
        secondFloorWith(3);

        Alert first = alerts.set(student(3), "2F", 5);
        Alert second = alerts.set(student(3), "2F", 8);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(alerts.activeFor(student(3))).containsExactly(new Alert(first.id(), "2F", 8));
    }

    @Test
    void theAlertIsSentOnceWhenTheFloorReachesTheNumber() {
        secondFloorWith(3);
        alerts.set(student(3), "2F", 5);
        alerts.set(student(4), "2F", 10);
        clearInvocations(broker);

        assertThat(alerts.sendDue()).isZero();

        secondFloorWith(6); // three seats come back
        alerts.onSeatChange(new FreedSeat());

        verify(broker, times(1)).convertAndSendToUser(eq("al.student"), eq("/queue/notifications"),
            argThat((Map<String, Object> m) -> "FLOOR_FREE".equals(m.get("type")) && "2F".equals(m.get("floor"))
                && Integer.valueOf(6).equals(m.get("free"))));
        assertThat(alerts.activeFor(student(3))).isEmpty();
        assertThat(alerts.activeFor(student(4))).hasSize(1); // wants 10: still waiting

        clearInvocations(broker);
        assertThat(alerts.sendDue()).isZero(); // sent once only
        verify(broker, never()).convertAndSendToUser(eq("al.student"), any(), any(Object.class));
    }

    @Test
    void cancellingStopsTheAlert() {
        secondFloorWith(3);
        Alert alert = alerts.set(student(5), "2F", 5);

        alerts.cancel(student(5), alert.id());
        secondFloorWith(20);

        assertThat(alerts.sendDue()).isZero();
        assertThat(alerts.activeFor(student(5))).isEmpty();
    }

    @Test
    void theApiIsForStudents() throws Exception {
        mvc.perform(post("/api/alerts").with(user("al.student").roles("STUDENT")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"floorId\":\"1F\",\"minFree\":5}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ALREADY_FREE"));
        mvc.perform(get("/api/alerts").with(user("al.student").roles("STUDENT")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/alerts").with(user("g").roles("GATE")))
            .andExpect(status().isForbidden());
    }

    /** Any seat on any floor turning FREE. */
    private record FreedSeat() implements SeatStateEvent {
        @Override
        public String seatCode() {
            return "2F-A1";
        }

        @Override
        public String floorId() {
            return "2F";
        }

        @Override
        public SeatState newState() {
            return SeatState.FREE;
        }

        @Override
        public Instant at() {
            return Instant.now();
        }
    }
}
