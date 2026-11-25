package com.seatsense.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One time a student sits at a seat (table {@code seat_session}, V2). The database
 * allows at most one open (ACTIVE or ON_BREAK) session per seat and per student.
 */
@Entity
@Table(name = "seat_session")
public class SeatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SessionStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    /** Set only while ON_BREAK (a CHECK in V2 enforces it). */
    @Column(name = "break_until")
    private Instant breakUntil;

    @Column(name = "breaks_used", nullable = false)
    private short breaksUsed;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 16)
    private EndReason endReason;

    /** Shared by the sessions of one "Sit together" group (S2.3). */
    @Column(name = "group_id")
    private UUID groupId;

    protected SeatSession() {
        // for JPA
    }

    SeatSession(Long studentId, Long seatId, Instant startedAt, UUID groupId) {
        this.studentId = studentId;
        this.seatId = seatId;
        this.status = SessionStatus.ACTIVE;
        this.startedAt = startedAt;
        this.groupId = groupId;
    }

    /** Ends the session. Any break end time is cleared in the same update, as V2's CHECK requires. */
    void end(EndReason reason, Instant at) {
        this.status = SessionStatus.ENDED;
        this.endReason = reason;
        this.endedAt = at;
        this.breakUntil = null;
    }

    public Long getId() {
        return id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public Long getSeatId() {
        return seatId;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public Instant getBreakUntil() {
        return breakUntil;
    }

    public short getBreaksUsed() {
        return breaksUsed;
    }

    public EndReason getEndReason() {
        return endReason;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public boolean isOpen() {
        return status != SessionStatus.ENDED;
    }
}
