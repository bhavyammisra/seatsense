package com.seatsense.analytics;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Runs an action at most once per {@code minGap}, however often it is asked to.
 * Requests that arrive while a run is already scheduled are folded into that run,
 * so a burst of seat changes (an exam rush, a group of four sitting down) becomes
 * one availability update instead of dozens. The last request is never lost: if
 * anything was asked for, a run happens after it.
 *
 * <p>Uses the machine's clock, not the library clock: this is about not flooding
 * screens, and must not speed up when the dev simulator fast-forwards time.
 */
final class CoalescingThrottle {

    private final long minGapNanos;
    private final ScheduledExecutorService scheduler;
    private final Runnable action;
    private final LongSupplier nanoTime;

    private boolean scheduled;
    private long lastRun = Long.MIN_VALUE;

    CoalescingThrottle(Duration minGap, ScheduledExecutorService scheduler, Runnable action, LongSupplier nanoTime) {
        this.minGapNanos = minGap.toNanos();
        this.scheduler = scheduler;
        this.action = action;
        this.nanoTime = nanoTime;
    }

    /** Asks for a run. Returns at once; the run happens on the scheduler's thread. */
    synchronized void request() {
        if (scheduled) {
            return;
        }
        scheduled = true;
        long now = nanoTime.getAsLong();
        long wait = lastRun == Long.MIN_VALUE ? 0 : Math.max(0, lastRun + minGapNanos - now);
        scheduler.schedule(this::run, wait, TimeUnit.NANOSECONDS);
    }

    private void run() {
        synchronized (this) {
            scheduled = false;
            lastRun = nanoTime.getAsLong();
        }
        action.run();
    }
}
