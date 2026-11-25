package com.seatsense.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TimeProviderTest {

    private static final Instant START = Instant.parse("2026-11-25T10:00:00Z");
    private final TimeProvider time = new TimeProvider(Clock.fixed(START, ZoneOffset.UTC));

    @Test
    void returnsTheBaseClockTime() {
        assertThat(time.now()).isEqualTo(START);
    }

    @Test
    void advanceMovesNowAndClockForward() {
        time.advance(Duration.ofMinutes(30));
        time.advance(Duration.ofHours(2));

        Instant expected = START.plus(Duration.ofMinutes(150));
        assertThat(time.now()).isEqualTo(expected);
        assertThat(time.clock().instant()).isEqualTo(expected);
    }

    @Test
    void cannotMoveBackwards() {
        assertThatThrownBy(() -> time.advance(Duration.ofSeconds(-1)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
