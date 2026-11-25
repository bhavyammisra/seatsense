package com.seatsense.session;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Break rules (docs/PROJECT.md decision D5), set in application.yml under
 * {@code seatsense.breaks}. Defaults: 30-minute breaks, 2 per student per day.
 */
@ConfigurationProperties("seatsense.breaks")
public record SessionSettings(Duration length, Integer maxPerDay) {

    public SessionSettings {
        length = length == null ? Duration.ofMinutes(30) : length;
        maxPerDay = maxPerDay == null ? 2 : maxPerDay;
    }
}
