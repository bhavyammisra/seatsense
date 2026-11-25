package com.seatsense.allocation;

import com.seatsense.layout.Zone;
import java.util.Set;

/**
 * What a student would like in a seat. All optional; {@link #NONE} means no
 * preference, and the ranking then only looks at quietness and walking.
 */
public record SeatPreferences(Set<Zone> zones, boolean wantsPower, boolean wantsWindow, boolean needsAccessible) {

    public static final SeatPreferences NONE = new SeatPreferences(Set.of(), false, false, false);

    public SeatPreferences {
        zones = zones == null ? Set.of() : Set.copyOf(zones);
    }

    /** Number of things the student asked for. */
    int requestedCount() {
        return (zones.isEmpty() ? 0 : 1) + (wantsPower ? 1 : 0) + (wantsWindow ? 1 : 0) + (needsAccessible ? 1 : 0);
    }
}
