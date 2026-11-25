package com.seatsense.layout;

import com.seatsense.common.NotFoundException;
import com.seatsense.layout.LayoutViews.FloorView;
import com.seatsense.layout.LayoutViews.SeatView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public, no-login layout endpoints (docs/api.md, "Public"). */
@RestController
@RequestMapping("/api/public/floors")
public class PublicLayoutController {

    private final LayoutService layout;

    public PublicLayoutController(LayoutService layout) {
        this.layout = layout;
    }

    @GetMapping
    public List<FloorView> floors() {
        return layout.allFloors();
    }

    @GetMapping("/{floorId}/seats")
    public List<SeatView> seats(@PathVariable String floorId) {
        return layout.seatsOn(floorId)
            .orElseThrow(() -> new NotFoundException("No floor " + floorId + "."));
    }
}
