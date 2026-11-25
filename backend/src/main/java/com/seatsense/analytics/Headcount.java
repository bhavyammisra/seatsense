package com.seatsense.analytics;

import java.util.List;

/**
 * Who is in the library right now (docs/api.md, {@code GET /api/admin/headcount}).
 *
 * @param inside  students sitting in a seat (ACTIVE sessions)
 * @param floors  the same count per floor, lowest floor first
 * @param onBreak students who stepped out but still hold a seat (ON_BREAK sessions)
 */
public record Headcount(int inside, List<FloorHeadcount> floors, int onBreak) {

    public record FloorHeadcount(String id, int inside) {
    }
}
