package com.seatsense.analytics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/public/availability}: open to everyone, no names. */
@RestController
public class PublicAvailabilityController {

    private final AvailabilityService availability;

    public PublicAvailabilityController(AvailabilityService availability) {
        this.availability = availability;
    }

    @GetMapping("/api/public/availability")
    public Availability availability() {
        return availability.now();
    }
}
