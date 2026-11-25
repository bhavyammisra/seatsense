package com.seatsense.layout;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * One seat (table {@code seat}, V1). Whether it is free or taken is not stored
 * here: that comes from sessions and holds (docs/PROJECT.md Section 9). Only
 * {@code outOfService} is a property of the seat itself.
 */
@Entity
@Table(name = "seat")
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 16)
    private String code;

    @Column(name = "floor_id", nullable = false, length = 4)
    private String floorId;

    @Column(name = "zone_id", nullable = false, length = 40)
    private String zoneId;

    @Column(name = "row_label", nullable = false, length = 2)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private short seatNumber;

    /** Shared by seats at the same table or in the same room; null for counters. */
    @Column(name = "table_id", length = 40)
    private String tableId;

    @Column(nullable = false)
    private short gx;

    @Column(nullable = false)
    private short gy;

    @Column(name = "has_power", nullable = false)
    private boolean power;

    @Column(name = "near_window", nullable = false)
    private boolean window;

    @Column(nullable = false)
    private boolean accessible;

    @Column(name = "out_of_service", nullable = false)
    private boolean outOfService;

    /** Optimistic lock: two staff edits to the same seat cannot silently overwrite each other. */
    @Version
    @Column(nullable = false)
    private long version;

    protected Seat() {
        // for JPA
    }

    /** Staff take the seat out of use, or put it back. Rules about when are in the session module. */
    public void setOutOfService(boolean outOfService) {
        this.outOfService = outOfService;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getFloorId() {
        return floorId;
    }

    public String getZoneId() {
        return zoneId;
    }

    public String getRowLabel() {
        return rowLabel;
    }

    public short getSeatNumber() {
        return seatNumber;
    }

    public String getTableId() {
        return tableId;
    }

    public short getGx() {
        return gx;
    }

    public short getGy() {
        return gy;
    }

    public boolean hasPower() {
        return power;
    }

    public boolean isNearWindow() {
        return window;
    }

    public boolean isAccessible() {
        return accessible;
    }

    public boolean isOutOfService() {
        return outOfService;
    }

    public long getVersion() {
        return version;
    }
}
