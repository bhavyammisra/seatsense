package com.seatsense.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.seatsense.PostgresIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The "one seat, one person" proof (docs/PROJECT.md Section 11.3, WORKPLAN B1.2):
 * 200 threads go through the real {@link SessionService} at the same instant,
 * against a real Postgres, and the database guarantee holds every time.
 *
 * <p>Uses 200 throwaway students (card ids {@code PROOF-0001} to {@code PROOF-0200},
 * marked as sample data) and removes them and their sessions afterwards.
 */
class ConcurrencyProofTest extends PostgresIntegrationTest {

    private static final int THREADS = 200;

    @Autowired
    private SessionService sessions;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<Long> students = new ArrayList<>();

    @BeforeEach
    void createStudents() {
        for (int i = 1; i <= THREADS; i++) {
            students.add(jdbc.queryForObject(
                "insert into student (card_id, name, is_sample) values (?, ?, true) returning id",
                Long.class, "PROOF-%04d".formatted(i), "Proof Student %d".formatted(i)));
        }
    }

    @AfterEach
    void removeStudents() {
        jdbc.update("delete from seat_session where student_id in (select id from student where card_id like 'PROOF-%')");
        jdbc.update("delete from student where card_id like 'PROOF-%'");
    }

    /** Starts every task at the same moment and returns how many succeeded. */
    private int raceAndCountWinners(List<Callable<Boolean>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (Callable<Boolean> task : tasks) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).as("all threads ready").isTrue();
            go.countDown();
            int winners = 0;
            for (Future<Boolean> result : results) {
                if (result.get(60, TimeUnit.SECONDS)) {
                    winners++;
                }
            }
            return winners;
        } finally {
            pool.shutdownNow();
        }
    }

    private int openSessionsOnSeat(String code) {
        return jdbc.queryForObject("""
            select count(*) from seat_session s join seat on seat.id = s.seat_id
            where seat.code = ? and s.status in ('ACTIVE', 'ON_BREAK')""", Integer.class, code);
    }

    @Test
    void twoHundredStudentsGrabTheSameSeatAndExactlyOneGetsIt() throws Exception {
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (Long student : students) {
            tasks.add(() -> {
                try {
                    sessions.takeSeat(student, "3F-C1");
                    return true;
                } catch (SessionException.SeatTaken lost) {
                    return false;
                }
            });
        }

        int winners = raceAndCountWinners(tasks);

        assertThat(winners).isEqualTo(1);
        assertThat(openSessionsOnSeat("3F-C1")).isEqualTo(1);
    }

    @Test
    void oneStudentTriesTwoHundredSeatsAtOnceAndGetsExactlyOne() throws Exception {
        Long student = students.get(0);
        List<String> seatCodes = jdbc.queryForList(
            "select code from seat where floor_id = '3F' order by code limit ?", String.class, THREADS);
        assertThat(seatCodes).hasSize(THREADS - 56); // 3F has 144 seats; use every one of them
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (String code : seatCodes) {
            tasks.add(() -> {
                try {
                    sessions.takeSeat(student, code);
                    return true;
                } catch (SessionException.AlreadyInside lost) {
                    return false;
                }
            });
        }

        int winners = raceAndCountWinners(tasks);

        assertThat(winners).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "select count(*) from seat_session where student_id = ? and status in ('ACTIVE', 'ON_BREAK')",
            Integer.class, student)).isEqualTo(1);
    }

    @Test
    void twoHundredExitsOfOneSessionFreeTheSeatOnce() throws Exception {
        SeatSession session = sessions.takeSeat(students.get(0), "3F-C2");
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> {
                try {
                    sessions.leave(session.getId());
                    return true;
                } catch (InvalidSeatTransitionException alreadyLeft) {
                    return false;
                }
            });
        }

        assertThat(raceAndCountWinners(tasks)).isEqualTo(1);
        assertThat(openSessionsOnSeat("3F-C2")).isZero();
    }
}
