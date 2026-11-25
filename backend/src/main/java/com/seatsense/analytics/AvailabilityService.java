package com.seatsense.analytics;

import com.seatsense.analytics.Availability.FloorAvailability;
import com.seatsense.common.TimeProvider;
import com.seatsense.layout.Floor;
import com.seatsense.layout.FloorRepository;
import com.seatsense.layout.LayoutService;
import com.seatsense.layout.LayoutViews.SeatView;
import com.seatsense.session.SeatState;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Counts free seats per floor (WORKPLAN B1.4), with the same state rules as the seat map. */
@Service
public class AvailabilityService {

    private final FloorRepository floors;
    private final LayoutService layout;
    private final TimeProvider time;

    public AvailabilityService(FloorRepository floors, LayoutService layout, TimeProvider time) {
        this.floors = floors;
        this.layout = layout;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public Availability now() {
        List<FloorAvailability> perFloor = new ArrayList<>();
        int free = 0;
        int total = 0;
        for (Floor floor : floors.findAllByOrderByNumberAsc()) {
            List<SeatView> seats = layout.seatsOn(floor.getId()).orElse(List.of());
            int floorFree = (int) seats.stream().filter(s -> s.state() == SeatState.FREE).count();
            perFloor.add(new FloorAvailability(floor.getId(), floor.getName(), floorFree, seats.size(), floorFree == 0));
            free += floorFree;
            total += seats.size();
        }
        return new Availability(OffsetDateTime.now(time.clock()), free, total, free == 0, perFloor);
    }
}
