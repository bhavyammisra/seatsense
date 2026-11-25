package com.seatsense.session;

import com.seatsense.allocation.GridPoint;
import com.seatsense.allocation.SeatCandidate;
import com.seatsense.layout.Floor;
import com.seatsense.layout.FloorRepository;
import com.seatsense.layout.LayoutZone;
import com.seatsense.layout.LayoutZoneRepository;
import com.seatsense.layout.Seat;
import com.seatsense.layout.SeatRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Turns the layout tables into the plain inputs the allocation algorithms take. */
@Component
class SeatCatalog {

    private final FloorRepository floors;
    private final LayoutZoneRepository zones;
    private final SeatRepository seats;

    SeatCatalog(FloorRepository floors, LayoutZoneRepository zones, SeatRepository seats) {
        this.floors = floors;
        this.zones = zones;
        this.seats = seats;
    }

    /** Every seat in the library as an allocation candidate. */
    List<SeatCandidate> allCandidates() {
        Map<String, Floor> floorById = floors.findAll().stream()
            .collect(Collectors.toMap(Floor::getId, Function.identity()));
        Map<String, LayoutZone> zoneById = zones.findAll().stream()
            .collect(Collectors.toMap(LayoutZone::getId, Function.identity()));
        return seats.findAll().stream()
            .map(seat -> candidate(seat, floorById.get(seat.getFloorId()), zoneById.get(seat.getZoneId())))
            .toList();
    }

    /**
     * Where each floor's stairs and lift are on its grid. The seat map draws them
     * as the strip under the last row, in the middle, so that is the point used
     * for walking distance until the survey gives exact positions.
     */
    Map<String, GridPoint> entrances() {
        return floors.findAll().stream().collect(Collectors.toMap(
            Floor::getId,
            f -> new GridPoint(f.getGridWidth() / 2, f.getGridHeight() + 1)));
    }

    private static SeatCandidate candidate(Seat seat, Floor floor, LayoutZone zone) {
        return new SeatCandidate(
            seat.getCode(),
            seat.getFloorId(),
            floor.getNumber(),
            seat.getGx(),
            seat.getGy(),
            zone.getType(),
            seat.getTableId(),
            seat.hasPower(),
            seat.isNearWindow(),
            seat.isAccessible());
    }
}
