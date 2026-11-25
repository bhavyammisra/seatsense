package com.seatsense.waitlist;

import static org.assertj.core.api.Assertions.assertThat;

import com.seatsense.PostgresIntegrationTest;
import com.seatsense.session.EndReason;
import com.seatsense.session.SessionEvents.SeatFreed;
import com.seatsense.session.SessionException;
import com.seatsense.session.SessionService;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A freed seat is held for the waitlist at the same moment a walk-in takes it at the kiosk.
 * Exactly one of them may win: never a seat that is both held and taken.
 * Uses sample students SAMPLE0008 and SAMPLE0009 and seat 2F-E8, and removes everything afterwards.
 */
class SeatHoldRaceTest extends PostgresIntegrationTest {

    private static final String SEAT = "2F-E8";
    private static final int ROUNDS = 40;

    @Autowired
    private WaitlistService waitlist;

    @Autowired
    private SessionService sessions;

    @Autowired
    private JdbcTemplate jdbc;

    private long student(String card) {
        return jdbc.queryForObject("select id from student where card_id = ?", Long.class, card);
    }

    @AfterEach
    void cleanUp() {
        String mine = "select id from student where card_id in ('SAMPLE0008', 'SAMPLE0009')";
        jdbc.update("delete from seat_hold where student_id in (" + mine + ")");
        jdbc.update("delete from waitlist_entry where student_id in (" + mine + ")");
        jdbc.update("delete from seat_session where student_id in (" + mine + ")");
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class, SEAT);
    }

    @Test
    void aSeatIsNeverBothHeldAndTaken() throws Exception {
        long waiting = student("SAMPLE0008");
        long walkIn = student("SAMPLE0009");
        int both = 0;
        for (int round = 0; round < ROUNDS; round++) {
            waitlist.join(waiting, null);
            CyclicBarrier go = new CyclicBarrier(2);
            CompletableFuture<Void> offer = CompletableFuture.runAsync(() -> {
                await(go);
                waitlist.onSeatFreed(new SeatFreed(SEAT, "2F", EndReason.LEAVE, Instant.now()));
            });
            CompletableFuture<Void> take = CompletableFuture.runAsync(() -> {
                await(go);
                try {
                    sessions.takeSeat(walkIn, SEAT);
                } catch (SessionException.SeatTaken heldFirst) {
                    // the hold won: fine
                }
            });
            offer.get();
            take.get();

            int held = count("select count(*) from seat_hold h join seat s on s.id = h.seat_id "
                + "where s.code = ? and h.status = 'ACTIVE'");
            int taken = count("select count(*) from seat_session ss join seat s on s.id = ss.seat_id "
                + "where s.code = ? and ss.status <> 'ENDED'");
            if (held > 0 && taken > 0) {
                both++;
            }
            cleanUp();
        }
        assertThat(both).as("rounds where the seat was both held and taken").isZero();
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
