package com.seatsense.analytics;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/public/availability} and {@code /api/public/predictions}: open to everyone, no names. */
@RestController
public class PublicAvailabilityController {

    private final AvailabilityService availability;
    private final PredictionService predictions;
    private final AnalyticsService analytics;

    public PublicAvailabilityController(AvailabilityService availability, PredictionService predictions,
                                        AnalyticsService analytics) {
        this.availability = availability;
        this.predictions = predictions;
        this.analytics = analytics;
    }

    @GetMapping("/api/public/availability")
    public Availability availability() {
        return availability.now();
    }

    /** "Seats usually free up around X" per floor (B4.1). Empty answers when there is no history. */
    @GetMapping("/api/public/predictions")
    public PredictionService.Predictions predictions() {
        return predictions.now();
    }

    /**
     * The public site is showing FULL to someone (B4.3, "how many students saw FULL"). Counted only
     * while the library really is full. shortcut: no per-visitor rate limit, add one before a real launch.
     */
    @PostMapping("/api/public/saw-full")
    public Map<String, Boolean> sawFull() {
        return Map.of("counted", analytics.recordFullView());
    }
}
