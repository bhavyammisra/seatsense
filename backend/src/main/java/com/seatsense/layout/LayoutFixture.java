package com.seatsense.layout;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Furniture drawn on the seat map: table, counter, room or area (table {@code layout_fixture}, V1). */
@Entity
@Table(name = "layout_fixture")
public class LayoutFixture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "floor_id", nullable = false, length = 4)
    private String floorId;

    @Column(name = "zone_id", nullable = false, length = 40)
    private String zoneId;

    /** {@code table}, {@code counter}, {@code room} or {@code area}. */
    @Column(nullable = false, length = 10)
    private String kind;

    @Column(nullable = false)
    private short gx;

    @Column(nullable = false)
    private short gy;

    @Column(nullable = false)
    private short w;

    @Column(nullable = false)
    private short h;

    @Column(nullable = false, length = 60)
    private String label;

    protected LayoutFixture() {
        // for JPA
    }

    public Long getId() {
        return id;
    }

    public String getFloorId() {
        return floorId;
    }

    public String getZoneId() {
        return zoneId;
    }

    public String getKind() {
        return kind;
    }

    public short getGx() {
        return gx;
    }

    public short getGy() {
        return gy;
    }

    public short getW() {
        return w;
    }

    public short getH() {
        return h;
    }

    public String getLabel() {
        return label;
    }
}
