package com.seatsense.common;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    // Library time, not the server's: closing time, breaks and predictions must not shift on a UTC server.
    @Bean
    public TimeProvider timeProvider(@Value("${seatsense.time-zone:Asia/Kolkata}") ZoneId zone) {
        return new TimeProvider(Clock.system(zone));
    }
}
