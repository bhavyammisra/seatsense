package com.seatsense.analytics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/public/availability} and {@code /api/public/predictions}: open to everyone, no names. */
@RestController
public class PublicAvailabilityController {

    private final AvailabilityService availability;
    private final PredictionService predictions;

    public PublicAvailabilityController(AvailabilityService availability, PredictionService predictions) {
        this.availability = availability;
        this.predictions = predictions;
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
}
