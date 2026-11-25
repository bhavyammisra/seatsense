package com.seatsense.waitlist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A seat kept for one person on the waitlist (table {@code seat_hold}, V6). */
@Entity
@Table(name = "seat_hold")
public class SeatHold {

    static final String ACTIVE = "ACTIVE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(nullable = false, length = 16)
    private String type;

    /** ACTIVE, USED, EXPIRED or CANCELLED. */
    @Column(nullable = false, length = 10)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected SeatHold() {
        // for JPA
    }

    SeatHold(Long seatId, Long studentId, Instant createdAt, Instant expiresAt) {
        this.seatId = seatId;
        this.studentId = studentId;
        this.type = "WAITLIST_OFFER";
        this.status = ACTIVE;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    void extendTo(Instant newExpiry) {
        this.expiresAt = newExpiry;
    }

    void close(String finalStatus) {
        this.status = finalStatus;
    }

    public Long getId() {
        return id;
    }

    public Long getSeatId() {
        return seatId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
