package com.seatsense.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    @Bean
    public TimeProvider timeProvider() {
        return new TimeProvider(Clock.systemDefaultZone());
    }
}
