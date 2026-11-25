package com.seatsense.layout;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;

/** One floor of the library (table {@code floor}, V1). Read-only from the app. */
@Entity
@Table(name = "floor")
public class Floor {

    @Id
    @Column(length = 4)
    private String id;

    @Column(nullable = false)
    private short number;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(name = "short_name", nullable = false, length = 20)
    private String shortName;

    @Column(nullable = false, length = 120)
    private String landmark;

    @Column(name = "grid_width", nullable = false)
    private short gridWidth;

    @Column(name = "grid_height", nullable = false)
    private short gridHeight;

    /** Null until the real opening hours are known (survey, S0.4). */
    @Column(name = "open_time")
    private LocalTime openTime;

    /** Null until the real opening hours are known (survey, S0.4). */
    @Column(name = "close_time")
    private LocalTime closeTime;

    protected Floor() {
        // for JPA
    }

    public String getId() {
        return id;
    }

    public short getNumber() {
        return number;
    }

    public String getName() {
        return name;
    }

    public String getShortName() {
        return shortName;
    }

    public String getLandmark() {
        return landmark;
    }

    public short getGridWidth() {
        return gridWidth;
    }

    public short getGridHeight() {
        return gridHeight;
    }

    public LocalTime getOpenTime() {
        return openTime;
    }

    public LocalTime getCloseTime() {
        return closeTime;
    }
}
