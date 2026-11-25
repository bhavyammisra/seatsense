package com.seatsense.waitlist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One student's place in the waitlist (table {@code waitlist_entry}, V6). */
@Entity
@Table(name = "waitlist_entry")
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    /** Null: any floor. */
    @Column(name = "floor_pref", length = 4)
    private String floorPref;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private WaitlistStatus status;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "offered_seat_id")
    private Long offeredSeatId;

    @Column(name = "offer_expires_at")
    private Instant offerExpiresAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected WaitlistEntry() {
        // for JPA
    }

    WaitlistEntry(Long studentId, String floorPref, Instant joinedAt) {
        this.studentId = studentId;
        this.floorPref = floorPref;
        this.status = WaitlistStatus.WAITING;
        this.joinedAt = joinedAt;
    }

    void offer(Long seatId, Instant expiresAt) {
        this.status = WaitlistStatus.OFFERED;
        this.offeredSeatId = seatId;
        this.offerExpiresAt = expiresAt;
    }

    void accept(Instant expiresAt) {
        this.status = WaitlistStatus.ACCEPTED;
        this.offerExpiresAt = expiresAt;
    }

    void end(WaitlistStatus finalStatus, Instant at) {
        this.status = finalStatus;
        this.endedAt = at;
        // The CHECK in V6: only OFFERED and ACCEPTED carry an offered seat and expiry.
        this.offeredSeatId = null;
        this.offerExpiresAt = null;
    }

    public Long getId() {
        return id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public String getFloorPref() {
        return floorPref;
    }

    public WaitlistStatus getStatus() {
        return status;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Long getOfferedSeatId() {
        return offeredSeatId;
    }

    public Instant getOfferExpiresAt() {
        return offerExpiresAt;
    }
}
