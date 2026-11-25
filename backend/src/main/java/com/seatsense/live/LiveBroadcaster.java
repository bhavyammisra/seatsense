package com.seatsense.live;

import com.seatsense.common.GateScanEvent;
import com.seatsense.common.SeatStateEvent;
import com.seatsense.session.SeatState;
import java.time.Instant;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Pushes domain events to every open screen (docs/api.md, WebSocket). */
@Component
public class LiveBroadcaster {

    /** Body of /topic/seats/{floorId}. */
    public record SeatUpdate(String code, SeatState state, Instant at) {
    }

    private final SimpMessagingTemplate broker;

    public LiveBroadcaster(SimpMessagingTemplate broker) {
        this.broker = broker;
    }

    // After commit only: a seat must never flash TAKEN on screens if the transaction that took it rolls back.
    // fallbackExecution: also fires when an event is published outside a transaction.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSeatChange(SeatStateEvent event) {
        broker.convertAndSend("/topic/seats/" + event.floorId(),
            new SeatUpdate(event.seatCode(), event.newState(), event.at()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onGateScan(GateScanEvent event) {
        broker.convertAndSend("/topic/gate/" + event.gateId(), event.message());
    }
}
