package com.seatsense.common;

/**
 * Implemented by the gate's CardScanned event (docs/api.md). The live module sends {@link #message()} to
 * /topic/gate/{gateId} so the kiosk, a different device from the phone scanner, knows what to show.
 */
public interface GateScanEvent {

    String gateId();

    /** The body sent to the kiosk: { type: "SCANNED", mode, student, lastSeat, heldSeat } as in docs/api.md. */
    Object message();
}
