package com.seatsense.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CoalescingThrottleTest {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicInteger runs = new AtomicInteger();
    private final List<Long> runTimes = new CopyOnWriteArrayList<>();

    @AfterEach
    void stop() {
        scheduler.shutdownNow();
    }

    private CoalescingThrottle throttle(Duration gap) {
        return new CoalescingThrottle(gap, scheduler, () -> {
            runs.incrementAndGet();
            runTimes.add(System.nanoTime());
        }, System::nanoTime);
    }

    @Test
    void aBurstOfRequestsBecomesOneRun() throws Exception {
        CoalescingThrottle throttle = throttle(Duration.ofMillis(300));

        for (int i = 0; i < 100; i++) {
            throttle.request();
        }
        Thread.sleep(150);

        assertThat(runs.get()).isEqualTo(1);
    }

    @Test
    void theLastRequestIsNeverLost() throws Exception {
        CoalescingThrottle throttle = throttle(Duration.ofMillis(200));

        throttle.request();
        Thread.sleep(50);
        throttle.request(); // arrives inside the gap: folded into one later run
        Thread.sleep(400);

        assertThat(runs.get()).isEqualTo(2);
    }

    @Test
    void runsAreAtLeastTheGapApart() throws Exception {
        CoalescingThrottle throttle = throttle(Duration.ofMillis(100));

        long end = System.nanoTime() + Duration.ofMillis(550).toNanos();
        while (System.nanoTime() < end) {
            throttle.request();
            Thread.sleep(5);
        }
        Thread.sleep(250);

        assertThat(runs.get()).isBetween(2, 7);
        for (int i = 1; i < runTimes.size(); i++) {
            long gapMillis = Duration.ofNanos(runTimes.get(i) - runTimes.get(i - 1)).toMillis();
            assertThat(gapMillis).as("gap between run %d and %d", i, i + 1).isGreaterThanOrEqualTo(95);
        }
    }
}
