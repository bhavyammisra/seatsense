package com.seatsense.analytics;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Free seats right now (docs/api.md, {@code GET /api/public/availability} and
 * {@code /topic/availability}). Out-of-service seats count in {@code total} but
 * are never free.
 */
public record Availability(OffsetDateTime updatedAt, int free, int total, boolean full, List<FloorAvailability> floors) {

    public record FloorAvailability(String id, String name, int free, int total, boolean full) {
    }
}
