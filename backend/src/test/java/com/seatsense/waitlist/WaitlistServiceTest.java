package com.seatsense.waitlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.seatsense.PostgresIntegrationTest;
import com.seatsense.auth.AppUser;
import com.seatsense.auth.AppUserRepository;
import com.seatsense.auth.Role;
import com.seatsense.common.TimeProvider;
import com.seatsense.session.EndReason;
import com.seatsense.session.SeatSession;
import com.seatsense.session.SessionEvents.SeatFreed;
import com.seatsense.session.SeatState;
import com.seatsense.session.SessionException;
import com.seatsense.session.SessionService;
import com.seatsense.waitlist.WaitlistService.Place;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The waitlist (WORKPLAN B2.1 to B2.3). Uses sample students SAMPLE0016 to SAMPLE0020
 * and floor 2F rows D and E, and removes everything it created afterwards.
 */
@AutoConfigureMockMvc
class WaitlistServiceTest extends PostgresIntegrationTest {

    private static final String MINE = "select id from student where card_id between 'SAMPLE0016' and 'SAMPLE0020'";

    @Autowired
    private WaitlistService waitlist;

    @Autowired
    private SessionService sessions;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TimeProvider time;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private MockMvc mvc;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from seat_hold where student_id in (" + MINE + ")");
        jdbc.update("delete from waitlist_entry where student_id in (" + MINE + ")");
        jdbc.update("delete from seat_session where student_id in (" + MINE + ")");
        jdbc.update("delete from app_user where username like 'wl.%'");
    }

    private long student(int n) {
        return jdbc.queryForObject("select id from student where card_id = ?", Long.class, "SAMPLE%04d".formatted(n));
    }

    /** Someone sits at the seat and leaves again, which frees it and triggers an offer. */
    private void freeUp(String seatCode, int byStudent) {
        SeatSession s = sessions.takeSeat(student(byStudent), seatCode);
        sessions.leave(s.getId());
    }

    @Test
    void theQueueIsFirstComeFirstServed() {
        Place first = waitlist.join(student(16), null);
        Place second = waitlist.join(student(17), null);

        assertThat(first.status()).isEqualTo("WAITING");
        assertThat(first.position()).isEqualTo(1);
        assertThat(second.position()).isEqualTo(2);
        assertThat(waitlist.placeOf(student(18)).status()).isEqualTo("NONE");
    }

    @Test
    void joiningTwiceOrWhileInsideIsRefused() {
        waitlist.join(student(16), null);
        assertThatThrownBy(() -> waitlist.join(student(16), null))
            .isInstanceOfSatisfying(WaitlistException.class, e -> assertThat(e.errorCode()).isEqualTo("ALREADY_WAITING"));

        sessions.takeSeat(student(17), "2F-D1");
        assertThatThrownBy(() -> waitlist.join(student(17), null))
            .isInstanceOfSatisfying(WaitlistException.class, e -> assertThat(e.errorCode()).isEqualTo("ALREADY_INSIDE"));
    }

    @Test
    void aFreedSeatIsHeldForTheNextPersonAndOnlyTheyCanTakeIt() {
        waitlist.join(student(16), null);

        freeUp("2F-D2", 20);

        Place offered = waitlist.placeOf(student(16));
        assertThat(offered.status()).isEqualTo("OFFERED");
        assertThat(offered.offeredSeat()).isEqualTo("2F-D2");
        assertThat(offered.offerExpiresAt()).isAfter(time.now());
        assertThat(sessions.claimedSeats("2F")).containsEntry("2F-D2", SeatState.HELD);
        assertThat(sessions.heldSeatOf(student(16))).contains("2F-D2");

        assertThatThrownBy(() -> sessions.takeSeat(student(19), "2F-D2"))
            .isInstanceOf(SessionException.SeatTaken.class);

        sessions.takeSeat(student(16), "2F-D2");
        assertThat(waitlist.placeOf(student(16)).status()).isEqualTo("NONE");
        assertThat(waitlist.activeHolds()).doesNotContainKey("2F-D2");
        assertThat(sessions.claimedSeats("2F")).containsEntry("2F-D2", SeatState.TAKEN);
    }

    @Test
    void acceptingRestartsTheHoldOnceOnly() {
        waitlist.join(student(16), null);
        freeUp("2F-D3", 20);
        time.advance(Duration.ofMinutes(10));

        Place accepted = waitlist.accept(student(16));

        assertThat(accepted.status()).isEqualTo("ACCEPTED");
        assertThat(Duration.between(time.now(), accepted.offerExpiresAt())).isBetween(
            Duration.ofMinutes(14), Duration.ofMinutes(15));
        assertThatThrownBy(() -> waitlist.accept(student(16)))
            .isInstanceOfSatisfying(WaitlistException.class, e -> assertThat(e.errorCode()).isEqualTo("NO_OFFER"));
    }

    @Test
    void anOfferThatRunsOutGoesToTheNextPerson() {
        waitlist.join(student(16), null);
        waitlist.join(student(17), null);
        freeUp("2F-D4", 20);
        assertThat(waitlist.placeOf(student(16)).offeredSeat()).isEqualTo("2F-D4");

        time.advance(Duration.ofMinutes(16));
        waitlist.expireHolds();

        assertThat(waitlist.placeOf(student(16)).status()).isEqualTo("NONE");
        Place next = waitlist.placeOf(student(17));
        assertThat(next.status()).isEqualTo("OFFERED");
        assertThat(next.offeredSeat()).isEqualTo("2F-D4");
    }

    @Test
    void floorPreferenceIsRespected() {
        waitlist.join(student(16), "3F");
        waitlist.join(student(17), null);

        freeUp("2F-D5", 20);

        assertThat(waitlist.placeOf(student(16)).status()).isEqualTo("WAITING");
        assertThat(waitlist.placeOf(student(17)).offeredSeat()).isEqualTo("2F-D5");
    }

    @Test
    void positionCountsOnlyPeopleWaitingForSeatsYouCouldTake() {
        waitlist.join(student(16), "3F");
        Place secondFloor = waitlist.join(student(17), "2F");
        Place anyFloor = waitlist.join(student(18), null);

        assertThat(secondFloor.position()).isEqualTo(1); // the 3F student is not after 2F seats
        assertThat(anyFloor.position()).isEqualTo(3);
    }

    @Test
    void seatsFreedByTheClosingResetAreNotOffered() {
        waitlist.join(student(16), null);

        waitlist.onSeatFreed(new SeatFreed("2F-D2", "2F", EndReason.CLOSING, time.now()));

        assertThat(waitlist.placeOf(student(16)).status()).isEqualTo("WAITING");
        assertThat(waitlist.activeHolds()).doesNotContainKey("2F-D2");
    }

    @Test
    void anOfferThatRanOutCannotBeAcceptedBeforeTheJobClearsIt() {
        waitlist.join(student(16), null);
        freeUp("2F-D3", 20);

        time.advance(Duration.ofMinutes(16));

        assertThatThrownBy(() -> waitlist.accept(student(16)))
            .isInstanceOfSatisfying(WaitlistException.class, e -> assertThat(e.errorCode()).isEqualTo("NO_OFFER"));
    }

    @Test
    void leavingTheQueuePassesAnOfferedSeatOn() {
        waitlist.join(student(16), null);
        waitlist.join(student(17), null);
        freeUp("2F-D6", 20);

        waitlist.leave(student(16));

        assertThat(waitlist.placeOf(student(16)).status()).isEqualTo("NONE");
        assertThat(waitlist.placeOf(student(17)).offeredSeat()).isEqualTo("2F-D6");
    }

    @Test
    void takingADifferentSeatReleasesTheHeldOne() {
        waitlist.join(student(16), null);
        waitlist.join(student(17), null);
        freeUp("2F-E1", 20);

        sessions.takeSeat(student(16), "2F-E5");

        assertThat(waitlist.placeOf(student(17)).offeredSeat()).isEqualTo("2F-E1");
    }

    @Test
    void theApiWorksForAStudentAccount() throws Exception {
        users.save(new AppUser("wl.student", "{noop}unused", Role.STUDENT, student(18)));

        mvc.perform(get("/api/waitlist/me").with(user("wl.student").roles("STUDENT")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NONE"));
        mvc.perform(post("/api/waitlist").with(user("wl.student").roles("STUDENT")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"floorPref\":\"2F\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("WAITING"))
            .andExpect(jsonPath("$.position").isNumber());
        mvc.perform(post("/api/waitlist").with(user("wl.student").roles("STUDENT")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ALREADY_WAITING"));
        mvc.perform(post("/api/waitlist/accept").with(user("wl.student").roles("STUDENT")).with(csrf()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("NO_OFFER"));
        mvc.perform(delete("/api/waitlist").with(user("wl.student").roles("STUDENT")).with(csrf()))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/waitlist/me").with(user("wl.student").roles("STUDENT")))
            .andExpect(jsonPath("$.status").value("NONE"));
    }
}
