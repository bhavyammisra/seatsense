package com.seatsense.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seatsense.PostgresIntegrationTest;
import com.seatsense.common.SeatStateEvent;
import com.seatsense.common.TimeProvider;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * Breaks, expiry, closing time, groups and best seat (WORKPLAN S2.1 to S2.5).
 * Uses sample students SAMPLE0011 to SAMPLE0015 and floor 1F.
 */
@RecordApplicationEvents
class SessionLifecycleTest extends PostgresIntegrationTest {

    @Autowired
    private SessionService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TimeProvider time;

    @Autowired
    private ApplicationEvents events;

    @AfterEach
    void cleanUp() {
        jdbc.update("""
            delete from seat_session where student_id in (
              select id from student where card_id between 'SAMPLE0011' and 'SAMPLE0015')""");
        jdbc.update("update floor set close_time = null");
    }

    private long student(int n) {
        return jdbc.queryForObject("select id from student where card_id = ?", Long.class, "SAMPLE%04d".formatted(n));
    }

    @Test
    void aBreakKeepsTheSeatAndReturningGivesItBack() {
        SeatSession s = service.takeSeat(student(11), "1F-J1");

        SeatSession onBreak = service.startBreak(s.getId());
        assertThat(onBreak.getStatus()).isEqualTo(SessionStatus.ON_BREAK);
        assertThat(onBreak.getBreakUntil()).isAfter(time.now());
        assertThat(service.claimedSeats("1F")).containsEntry("1F-J1", SeatState.ON_BREAK);

        SeatSession back = service.returnFromBreak(s.getId());
        assertThat(back.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(back.getBreakUntil()).isNull();
        assertThat(service.claimedSeats("1F")).containsEntry("1F-J1", SeatState.TAKEN);
    }

    @Test
    void breaksAreLimitedPerDayAcrossSessions() {
        long me = student(12);
        SeatSession first = service.takeSeat(me, "1F-J2");
        service.startBreak(first.getId());
        service.returnFromBreak(first.getId());
        service.leave(first.getId());

        // A new session the same day still counts the earlier break.
        SeatSession second = service.takeSeat(me, "1F-J2");
        service.startBreak(second.getId());
        service.returnFromBreak(second.getId());

        assertThatThrownBy(() -> service.startBreak(second.getId()))
            .isInstanceOf(SessionException.NoBreaksLeft.class);
    }

    @Test
    void returningAfterTheBreakRanOutIsRefusedAndTheSeatIsFreed() {
        SeatSession s = service.takeSeat(student(13), "1F-J3");
        service.startBreak(s.getId());

        time.advance(Duration.ofMinutes(31));

        assertThatThrownBy(() -> service.returnFromBreak(s.getId()))
            .isInstanceOf(InvalidSeatTransitionException.class);
        SeatSession ended = service.session(s.getId()).orElseThrow();
        assertThat(ended.getStatus()).isEqualTo(SessionStatus.ENDED);
        assertThat(ended.getEndReason()).isEqualTo(EndReason.BREAK_EXPIRED);
        assertThat(ended.getBreakUntil()).isNull();
    }

    @Test
    void theExpiryJobEndsBreaksThatRanOut() {
        SeatSession s = service.takeSeat(student(14), "1F-J4");
        service.startBreak(s.getId());
        time.advance(Duration.ofMinutes(31));

        assertThat(service.expireBreaks()).isGreaterThanOrEqualTo(1);

        assertThat(service.session(s.getId()).orElseThrow().getEndReason()).isEqualTo(EndReason.BREAK_EXPIRED);
        assertThat(service.claimedSeats("1F")).doesNotContainKey("1F-J4");
    }

    @Test
    void closingTimeEndsEverySessionOnThatFloorOnly() {
        SeatSession upstairs = service.takeSeat(student(11), "1F-J5");
        SeatSession downstairs = service.takeSeat(student(15), "GF-J5");
        jdbc.update("update floor set close_time = ? where id = '1F'",
            LocalTime.now(time.clock()).minusMinutes(1));

        service.closeFloorsPastClosingTime();

        assertThat(service.session(upstairs.getId()).orElseThrow().getEndReason()).isEqualTo(EndReason.CLOSING);
        assertThat(service.session(downstairs.getId()).orElseThrow().isOpen()).isTrue();
    }

    @Test
    void floorsWithoutAClosingTimeAreLeftAlone() {
        SeatSession s = service.takeSeat(student(11), "1F-J6");
        service.closeFloorsPastClosingTime();
        assertThat(service.session(s.getId()).orElseThrow().isOpen()).isTrue();
    }

    @Test
    void aGroupIsAllOrNothing() {
        service.takeSeat(student(11), "1F-A2");

        // A1 and A2 side by side, but A2 is taken: nobody in the group is seated.
        assertThatThrownBy(() -> service.takeGroup(List.of(student(12), student(13)), List.of("1F-A1", "1F-A2")))
            .isInstanceOf(SessionException.GroupNotAvailable.class);
        assertThat(service.openSessionOf(student(12))).isEmpty();
        assertThat(service.openSessionOf(student(13))).isEmpty();
    }

    @Test
    void aGroupWithoutSeatsGetsTheFindersBestOptionUnderOneGroupId() {
        List<SeatSession> group = service.takeGroup(List.of(student(12), student(13), student(14)), null);

        assertThat(group).hasSize(3);
        assertThat(group).extracting(SeatSession::getGroupId).doesNotContainNull().containsOnly(group.get(0).getGroupId());
    }

    @Test
    void bestSeatGivesAFreeSeat() {
        SeatSession s = service.takeBest(student(15), com.seatsense.allocation.SeatPreferences.NONE);
        assertThat(s.getStatus()).isEqualTo(SessionStatus.ACTIVE);
    }

    @Test
    void everySeatChangeReachesScreensThroughSeatStateEvent() {
        SeatSession s = service.takeSeat(student(11), "1F-J7");
        service.startBreak(s.getId());
        service.returnFromBreak(s.getId());
        service.leave(s.getId());

        assertThat(events.stream(SeatStateEvent.class).filter(e -> e.seatCode().equals("1F-J7")))
            .extracting(SeatStateEvent::newState)
            .containsExactly(SeatState.TAKEN, SeatState.ON_BREAK, SeatState.TAKEN, SeatState.FREE);
    }
}
