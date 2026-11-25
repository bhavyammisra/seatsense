package com.seatsense.gate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One card scanned at a gate (table {@code scan_event}, V2). Unknown cards are logged with no student. */
@Entity
@Table(name = "scan_event")
public class ScanEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_id", nullable = false, length = 32)
    private String cardId;

    @Column(name = "gate_id", nullable = false, length = 20)
    private String gateId;

    @Column(name = "student_id")
    private Long studentId;

    /** ENTRY, EXIT, RETURN or UNKNOWN_CARD. */
    @Column(nullable = false, length = 12)
    private String type;

    @Column(name = "scanned_at", nullable = false)
    private Instant scannedAt;

    protected ScanEvent() {
        // for JPA
    }

    ScanEvent(String cardId, String gateId, Long studentId, String type, Instant scannedAt) {
        this.cardId = cardId;
        this.gateId = gateId;
        this.studentId = studentId;
        this.type = type;
        this.scannedAt = scannedAt;
    }

    public Long getId() {
        return id;
    }

    public String getType() {
        return type;
    }
}
