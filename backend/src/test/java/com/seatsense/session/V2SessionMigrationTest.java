package com.seatsense.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seatsense.PostgresIntegrationTest;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The database guarantees from V2 (PROJECT.md Section 11.3), tested straight against Postgres with no Java
 * service in between: whatever the code does, a seat or a student can have only one open session.
 */
class V2SessionMigrationTest extends PostgresIntegrationTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-25T18:00:00+05:30");

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM seat_session");
        jdbc.update("DELETE FROM scan_event");
    }

    private long seat(int n) {
        return jdbc.queryForObject("SELECT id FROM seat ORDER BY id LIMIT 1 OFFSET ?", Long.class, n);
    }

    private long student(int n) {
        return jdbc.queryForObject("SELECT id FROM student WHERE card_id = ?", Long.class, "SAMPLE%04d".formatted(n));
    }

    private void open(long student, long seat, String status) {
        jdbc.update("""
            INSERT INTO seat_session (student_id, seat_id, status, started_at, break_until)
            VALUES (?, ?, ?, ?, ?)""",
            student, seat, status, NOW, status.equals("ON_BREAK") ? NOW.plusMinutes(30) : null);
    }

    private void ended(long student, long seat) {
        jdbc.update("""
            INSERT INTO seat_session (student_id, seat_id, status, started_at, ended_at, end_reason)
            VALUES (?, ?, 'ENDED', ?, ?, 'LEAVE')""", student, seat, NOW.minusHours(2), NOW.minusHours(1));
    }

    @Test
    void secondActiveSessionOnSameSeatIsRejected() {
        open(student(1), seat(0), "ACTIVE");
        assertThatThrownBy(() -> open(student(2), seat(0), "ACTIVE")).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void seatOnBreakIsStillTaken() {
        open(student(1), seat(0), "ON_BREAK");
        assertThatThrownBy(() -> open(student(2), seat(0), "ACTIVE")).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void studentCannotHoldTwoSeats() {
        open(student(1), seat(0), "ACTIVE");
        assertThatThrownBy(() -> open(student(1), seat(1), "ACTIVE")).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void endedSessionsDoNotBlockTheSeatOrTheStudent() {
        ended(student(1), seat(0));
        ended(student(2), seat(0));
        ended(student(1), seat(1));

        open(student(3), seat(0), "ACTIVE");
        open(student(1), seat(2), "ACTIVE");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM seat_session", Integer.class)).isEqualTo(5);
    }

    @Test
    void endedSessionNeedsEndTimeAndReason() {
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO seat_session (student_id, seat_id, status, started_at)
            VALUES (?, ?, 'ENDED', ?)""", student(1), seat(0), NOW))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void breakNeedsAnEndTimeAndOnlyWhileOnBreak() {
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO seat_session (student_id, seat_id, status, started_at)
            VALUES (?, ?, 'ON_BREAK', ?)""", student(1), seat(0), NOW))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO seat_session (student_id, seat_id, status, started_at, break_until)
            VALUES (?, ?, 'ACTIVE', ?, ?)""", student(1), seat(0), NOW, NOW.plusMinutes(30)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void scansLogKnownAndUnknownCards() {
        jdbc.update("INSERT INTO scan_event (card_id, gate_id, student_id, type, scanned_at) VALUES (?, 'MAIN', ?, 'ENTRY', ?)",
            "SAMPLE0001", student(1), NOW);
        jdbc.update("INSERT INTO scan_event (card_id, gate_id, type, scanned_at) VALUES ('NOPE', 'MAIN', 'UNKNOWN_CARD', ?)",
            NOW);
        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO scan_event (card_id, gate_id, type, scanned_at) VALUES ('NOPE', 'MAIN', 'ENTRY', ?)", NOW))
            .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM scan_event", Integer.class)).isEqualTo(2);
    }
}
