package com.seatsense.layout;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One area of a floor, drawn as a section of the seat map (table {@code zone}, V1).
 * Named LayoutZone because {@link Zone} is the zone type (QUIET, GENERAL, DISCUSSION).
 */
@Entity
@Table(name = "zone")
public class LayoutZone {

    /** {@code <floor>-<section>}, for example {@code 1F-tables}. */
    @Id
    @Column(length = 40)
    private String id;

    @Column(name = "floor_id", nullable = false, length = 4)
    private String floorId;

    /** The section id the frontend uses, for example {@code tables}. */
    @Column(name = "section_code", nullable = false, length = 30)
    private String sectionCode;

    @Column(nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Zone type;

    @Column(nullable = false, length = 200)
    private String note;

    /** False for areas that are shown but cannot be booked (seminar hall, lounge, stacks). */
    @Column(nullable = false)
    private boolean bookable;

    @Column(name = "from_row", nullable = false)
    private short fromRow;

    @Column(name = "to_row", nullable = false)
    private short toRow;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    protected LayoutZone() {
        // for JPA
    }

    public String getId() {
        return id;
    }

    public String getFloorId() {
        return floorId;
    }

    public String getSectionCode() {
        return sectionCode;
    }

    public String getName() {
        return name;
    }

    public Zone getType() {
        return type;
    }

    public String getNote() {
        return note;
    }

    public boolean isBookable() {
        return bookable;
    }

    public short getFromRow() {
        return fromRow;
    }

    public short getToRow() {
        return toRow;
    }

    public short getSortOrder() {
        return sortOrder;
    }
}
