package com.seatsense.session;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Break settings and the scheduled jobs (break expiry, closing time). */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(SessionSettings.class)
class SessionConfig {
}
