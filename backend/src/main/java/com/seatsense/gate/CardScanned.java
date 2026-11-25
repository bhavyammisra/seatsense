package com.seatsense.gate;

import com.seatsense.common.GateScanEvent;
import com.seatsense.gate.GateViews.ScanResult;

/**
 * A card was scanned at a gate (docs/api.md, CardScanned). The live module sends
 * {@link #message()} to {@code /topic/gate/{gateId}}, so the kiosk, a different
 * device from the phone scanner, knows what to show. Only the GATE role can
 * listen there, because it carries the student's name.
 */
public record CardScanned(String gateId, ScanResult result) implements GateScanEvent {

    @Override
    public Object message() {
        return result;
    }
}
