package com.seatsense.layout;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.seatsense.session.SeatState;
import java.util.List;

/** JSON shapes of the public layout API (docs/api.md, "Shared objects"). */
public final class LayoutViews {

    private LayoutViews() {
    }

    public record FloorView(
        String id,
        int number,
        String name,
        @JsonProperty("short") String shortName,
        String landmark,
        int gridWidth,
        int gridHeight,
        List<SectionView> sections,
        List<FixtureView> fixtures) {
    }

    public record SectionView(
        String id,
        String name,
        Zone zone,
        String note,
        boolean bookable,
        int fromRow,
        int toRow) {
    }

    public record FixtureView(String kind, String section, int gx, int gy, int w, int h, String label) {
    }

    public record SeatView(
        String code,
        String floor,
        String section,
        Zone zone,
        String row,
        int number,
        String tableId,
        int gx,
        int gy,
        boolean power,
        boolean window,
        boolean accessible,
        SeatState state) {
    }
}
