package com.seatsense.layout;

import com.seatsense.layout.LayoutViews.FixtureView;
import com.seatsense.layout.LayoutViews.FloorView;
import com.seatsense.layout.LayoutViews.SeatView;
import com.seatsense.layout.LayoutViews.SectionView;
import com.seatsense.session.SeatState;
import com.seatsense.session.SeatStateSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the public view of floors and seats (WORKPLAN S1.1). */
@Service
public class LayoutService {

    private final FloorRepository floors;
    private final LayoutZoneRepository zones;
    private final SeatRepository seats;
    private final LayoutFixtureRepository fixtures;
    private final ObjectProvider<SeatStateSource> stateSource;

    public LayoutService(
        FloorRepository floors,
        LayoutZoneRepository zones,
        SeatRepository seats,
        LayoutFixtureRepository fixtures,
        ObjectProvider<SeatStateSource> stateSource) {
        this.floors = floors;
        this.zones = zones;
        this.seats = seats;
        this.fixtures = fixtures;
        this.stateSource = stateSource;
    }

    /** Every floor with its sections and furniture, lowest floor first. No seats. */
    @Transactional(readOnly = true)
    public List<FloorView> allFloors() {
        Map<String, List<LayoutZone>> zonesByFloor = zones.findAllByOrderByFloorIdAscSortOrderAsc().stream()
            .collect(Collectors.groupingBy(LayoutZone::getFloorId));
        Map<String, LayoutZone> zoneById = zonesByFloor.values().stream()
            .flatMap(List::stream)
            .collect(Collectors.toMap(LayoutZone::getId, Function.identity()));
        Map<String, List<LayoutFixture>> fixturesByFloor = fixtures.findAllByOrderByIdAsc().stream()
            .collect(Collectors.groupingBy(LayoutFixture::getFloorId));

        return floors.findAllByOrderByNumberAsc().stream()
            .map(floor -> new FloorView(
                floor.getId(),
                floor.getNumber(),
                floor.getName(),
                floor.getShortName(),
                floor.getLandmark(),
                floor.getGridWidth(),
                floor.getGridHeight(),
                zonesByFloor.getOrDefault(floor.getId(), List.of()).stream().map(LayoutService::section).toList(),
                fixturesByFloor.getOrDefault(floor.getId(), List.of()).stream()
                    .map(f -> fixture(f, zoneById.get(f.getZoneId())))
                    .toList()))
            .toList();
    }

    /** Every seat on a floor with its current state, or empty if there is no such floor. */
    @Transactional(readOnly = true)
    public Optional<List<SeatView>> seatsOn(String floorId) {
        if (floorId == null || !floors.existsById(floorId)) {
            return Optional.empty();
        }
        Map<String, LayoutZone> zoneById = zones.findAllByOrderByFloorIdAscSortOrderAsc().stream()
            .filter(z -> z.getFloorId().equals(floorId))
            .collect(Collectors.toMap(LayoutZone::getId, Function.identity()));
        SeatStateSource source = stateSource.getIfAvailable();
        Map<String, SeatState> claimed = source == null ? Map.of() : source.claimedSeats(floorId);

        return Optional.of(seats.findByFloorIdOrderByGyAscGxAsc(floorId).stream()
            .map(seat -> seat(seat, zoneById.get(seat.getZoneId()), claimed))
            .toList());
    }

    /**
     * A seat's state for the map: out of service wins over everything, then
     * whatever the session module reports, and FREE otherwise.
     */
    static SeatState stateOf(Seat seat, Map<String, SeatState> claimed) {
        if (seat.isOutOfService()) {
            return SeatState.OUT_OF_SERVICE;
        }
        return claimed.getOrDefault(seat.getCode(), SeatState.FREE);
    }

    private static SectionView section(LayoutZone zone) {
        return new SectionView(zone.getSectionCode(), zone.getName(), zone.getType(), zone.getNote(),
            zone.isBookable(), zone.getFromRow(), zone.getToRow());
    }

    private static FixtureView fixture(LayoutFixture f, LayoutZone zone) {
        return new FixtureView(f.getKind(), zone == null ? null : zone.getSectionCode(),
            f.getGx(), f.getGy(), f.getW(), f.getH(), f.getLabel());
    }

    private static SeatView seat(Seat seat, LayoutZone zone, Map<String, SeatState> claimed) {
        return new SeatView(
            seat.getCode(),
            seat.getFloorId(),
            zone == null ? null : zone.getSectionCode(),
            zone == null ? null : zone.getType(),
            seat.getRowLabel(),
            seat.getSeatNumber(),
            seat.getTableId(),
            seat.getGx(),
            seat.getGy(),
            seat.hasPower(),
            seat.isNearWindow(),
            seat.isAccessible(),
            stateOf(seat, claimed));
    }
}
