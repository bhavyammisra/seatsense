package com.seatsense.analytics;

import com.seatsense.common.SeatStateEvent;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Pushes the availability counts to {@code /topic/availability} whenever a seat
 * changes, at most twice a second (docs/api.md, WebSocket).
 */
@Component
public class AvailabilityBroadcaster {

    static final String TOPIC = "/topic/availability";
    static final Duration MIN_GAP = Duration.ofMillis(500);

    private static final Logger log = LoggerFactory.getLogger(AvailabilityBroadcaster.class);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "availability-broadcast");
        t.setDaemon(true);
        return t;
    });
    private final CoalescingThrottle throttle;

    public AvailabilityBroadcaster(AvailabilityService availability, SimpMessagingTemplate broker) {
        this.throttle = new CoalescingThrottle(MIN_GAP, scheduler, () -> {
            try {
                broker.convertAndSend(TOPIC, availability.now());
            } catch (RuntimeException e) {
                // A failed push must not stop later ones; the next seat change sends fresh counts.
                log.warn("Could not send availability update", e);
            }
        }, System::nanoTime);
    }

    // After commit, like the seat topic: counts must match what is saved.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSeatChange(SeatStateEvent event) {
        throttle.request();
    }

    @PreDestroy
    void stop() {
        scheduler.shutdownNow();
    }
}
