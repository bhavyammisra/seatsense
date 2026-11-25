package com.seatsense.gate;

/** What a card scan at the gate means (docs/api.md, POST /api/gate/scan). */
public enum ScanMode {
    /** Not inside: show the welcome screen and seat picker. */
    ENTRY,
    /** Sitting in a seat: ask Leaving or Break. */
    EXIT,
    /** On a break that has not run out: straight back to the same seat. */
    RETURN
}
