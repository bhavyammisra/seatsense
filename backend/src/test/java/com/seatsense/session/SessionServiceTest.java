package com.seatsense.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seatsense.PostgresIntegrationTest;
import com.seatsense.session.SessionEvents.SeatFreed;
import com.seatsense.session.SessionEvents.SeatTaken;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * Session service against Postgres with V1 and V2 applied. Uses sample students
 * SAMPLE0031 to SAMPLE0040 and floor 2F, and removes its own sessions afterwards.
 */
@RecordApplicationEvents
class SessionServiceTest extends PostgresIntegrationTest {

    @Autowired
    private SessionService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationEvents events;

    private long student(int n) {
        return jdbc.queryForObject("select id from student where card_id = ?", Long.class,
            "SAMPLE%04d".formatted(n));
    }

    @AfterEach
    void removeTestSessions() {
        jdbc.update("""
            delete from seat_session where student_id in (
              select id from student where card_id between 'SAMPLE0031' and 'SAMPLE0040')""");
        jdbc.update("update seat set out_of_service = false where code = '2F-F1'");
    }

    @Test
    void takingAFreeSeatStartsAnActiveSessionAndAnnouncesIt() {
        long aarav = student(31);

        SeatSession session = service.takeSeat(aarav, "2F-A1");

        assertThat(session.getId()).isNotNull();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(session.getStartedAt()).isNotNull();
        assertThat(service.claimedSeats("2F")).containsEntry("2F-A1", SeatState.TAKEN);
        assertThat(service.openSessionOf(aarav)).isPresent();
        assertThat(events.stream(SeatTaken.class)).singleElement().satisfies(e -> {
            assertThat(e.seatCode()).isEqualTo("2F-A1");
            assertThat(e.floorId()).isEqualTo("2F");
            assertThat(e.studentId()).isEqualTo(aarav);
            assertThat(e.sessionId()).isEqualTo(session.getId());
        });
    }

    @Test
    void aTakenSeatIsRefusedWithTheNearestFreeSeatAsAlternative() {
        service.takeSeat(student(31), "2F-A1");

        assertThatThrownBy(() -> service.takeSeat(student(32), "2F-A1"))
            .isInstanceOfSatisfying(SessionException.SeatTaken.class, e -> {
                assertThat(e.errorCode()).isEqualTo("SEAT_TAKEN");
                assertThat(e.alternative()).isNotNull().isNotEqualTo("2F-A1").startsWith("2F-");
            });
        assertThat(service.openSessionOf(student(32))).isEmpty();
    }

    @Test
    void aStudentWhoAlreadyHasASeatCannotTakeAnother() {
        service.takeSeat(student(31), "2F-A1");

        assertThatThrownBy(() -> service.takeSeat(student(31), "2F-A2"))
            .isInstanceOfSatisfying(SessionException.AlreadyInside.class,
                e -> assertThat(e.errorCode()).isEqualTo("ALREADY_INSIDE"));
        assertThat(service.claimedSeats("2F")).doesNotContainKey("2F-A2");
    }

    @Test
    void anOutOfServiceSeatCannotBeTaken() {
        jdbc.update("update seat set out_of_service = true where code = '2F-F1'");

        assertThatThrownBy(() -> service.takeSeat(student(31), "2F-F1"))
            .isInstanceOf(SessionException.SeatUnavailable.class);
    }

    @Test
    void unknownSeatOrStudentIsNotFound() {
        assertThatThrownBy(() -> service.takeSeat(student(31), "2F-Z99"))
            .isInstanceOf(SessionException.NotFound.class);
        assertThatThrownBy(() -> service.takeSeat(-1L, "2F-A1"))
            .isInstanceOf(SessionException.NotFound.class);
    }

    @Test
    void leavingEndsTheSessionFreesTheSeatAndAnnouncesIt() {
        SeatSession session = service.takeSeat(student(31), "2F-A1");

        SeatSession ended = service.leave(session.getId());

        assertThat(ended.getStatus()).isEqualTo(SessionStatus.ENDED);
        assertThat(ended.getEndReason()).isEqualTo(EndReason.LEAVE);
        assertThat(ended.getEndedAt()).isNotNull();
        assertThat(service.claimedSeats("2F")).doesNotContainKey("2F-A1");
        assertThat(events.stream(SeatFreed.class)).singleElement().satisfies(e -> {
            assertThat(e.seatCode()).isEqualTo("2F-A1");
            assertThat(e.reason()).isEqualTo(EndReason.LEAVE);
        });

        // The seat and the student are both free again.
        assertThat(service.takeSeat(student(32), "2F-A1").getStatus()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(service.takeSeat(student(31), "2F-A2").getStatus()).isEqualTo(SessionStatus.ACTIVE);
    }

    @Test
    void leavingTwiceIsRefused() {
        SeatSession session = service.takeSeat(student(31), "2F-A1");
        service.leave(session.getId());

        assertThatThrownBy(() -> service.leave(session.getId()))
            .isInstanceOf(InvalidSeatTransitionException.class);
    }

    @Test
    void leavingAnUnknownSessionIsNotFound() {
        assertThatThrownBy(() -> service.leave(-1L)).isInstanceOf(SessionException.NotFound.class);
    }

    @Test
    void lastSeatIsTheMostRecentOne() {
        long aarav = student(31);
        assertThat(service.lastSeatOf(aarav)).isEmpty();

        service.leave(service.takeSeat(aarav, "2F-A1").getId());
        service.leave(service.takeSeat(aarav, "2F-A3").getId());

        assertThat(service.lastSeatOf(aarav)).contains("2F-A3");
    }

    @Test
    void claimedSeatsOnlyListsThatFloor() {
        service.takeSeat(student(31), "2F-A1");
        service.takeSeat(student(33), "GF-A1");

        Map<String, SeatState> second = service.claimedSeats("2F");
        assertThat(second).containsOnlyKeys("2F-A1");
    }

    @Test
    void theLayoutApiNowShowsTakenSeats() {
        service.takeSeat(student(31), "2F-A1");
        // LayoutService finds SessionService through SeatStateSource.
        assertThat(service).isInstanceOf(SeatStateSource.class);
        assertThat(service.claimedSeats("2F").get("2F-A1")).isEqualTo(SeatState.TAKEN);
    }

    @Test
    void whenTenStudentsGrabTheSameSeatAtOnceExactlyOneGetsIt() throws Exception {
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                long studentId = student(31 + i);
                Callable<Boolean> grab = () -> {
                    go.await();
                    try {
                        service.takeSeat(studentId, "2F-B1");
                        return true;
                    } catch (SessionException.SeatTaken lost) {
                        return false;
                    }
                };
                results.add(pool.submit(grab));
            }
            go.countDown();
            int winners = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    winners++;
                }
            }
            assertThat(winners).isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                select count(*) from seat_session s join seat on seat.id = s.seat_id
                where seat.code = '2F-B1' and s.status <> 'ENDED'""", Integer.class)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void whenTheSameSessionIsLeftTenTimesAtOnceOnlyOneSucceeds() throws Exception {
        SeatSession session = service.takeSeat(student(31), "2F-C1");
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    go.await();
                    try {
                        service.leave(session.getId());
                        return true;
                    } catch (InvalidSeatTransitionException alreadyLeft) {
                        return false;
                    }
                }));
            }
            go.countDown();
            int succeeded = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    succeeded++;
                }
            }
            assertThat(succeeded).isEqualTo(1);
            assertThat(events.stream(SeatFreed.class).filter(e -> e.seatCode().equals("2F-C1"))).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void indexNamesAreFoundAnywhereInTheErrorChain() {
        RuntimeException cause = new RuntimeException(
            "duplicate key value violates unique constraint \"seat_session_one_open_per_seat\"");
        RuntimeException wrapped = new RuntimeException("could not execute statement", cause);

        assertThat(SessionService.violates(wrapped, SessionService.ONE_OPEN_PER_SEAT)).isTrue();
        assertThat(SessionService.violates(wrapped, SessionService.ONE_OPEN_PER_STUDENT)).isFalse();
    }
}
