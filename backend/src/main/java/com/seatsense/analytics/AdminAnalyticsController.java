package com.seatsense.analytics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/admin/analytics} (WORKPLAN B4.3). LIBRARIAN or ADMIN (SecurityConfig, /api/admin/**). */
@RestController
public class AdminAnalyticsController {

    private final AnalyticsService analytics;

    public AdminAnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/api/admin/analytics")
    public AnalyticsService.Analytics analytics() {
        return analytics.now();
    }
}
