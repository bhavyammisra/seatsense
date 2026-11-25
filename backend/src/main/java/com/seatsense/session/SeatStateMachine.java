package com.seatsense.session;

import java.util.EnumSet;
import java.util.Set;

/**
 * The rules for how a seat moves between states (docs/PROJECT.md Section 11.4).
 *
 * <pre>
 * FREE ──TAKE──▶ TAKEN ──START_BREAK──▶ ON_BREAK ──RETURN──▶ TAKEN
 *                  │                       │
 *                  └──LEAVE──▶ FREE        └──BREAK_EXPIRED──▶ FREE
 *
 * FREE ──HOLD──▶ HELD ──TAKE (holder scans in)──▶ TAKEN
 *                  └──RELEASE_HOLD (expired or cancelled)──▶ FREE
 *
 * TAKEN, ON_BREAK, HELD ──CLOSE──▶ FREE         (closing-time reset)
 * FREE ──MARK_OUT_OF_SERVICE──▶ OUT_OF_SERVICE ──RETURN_TO_SERVICE──▶ FREE
 * </pre>
 *
 * Everything not drawn above is refused with {@link InvalidSeatTransitionException}.
 *
 * <p>This class only knows about states. Who is allowed to trigger an event (for
 * example, that only the person a seat is HELD for may TAKE it) is checked by the
 * session service, which owns the people involved.
 *
 * <p>Notes on choices that are not obvious:
 * <ul>
 *   <li>A seat can only go out of service when it is FREE, so staff never remove a
 *       seat from under someone. They wait until it is free.</li>
 *   <li>LEAVE is only allowed from TAKEN. A student on break who scans at the gate
 *       is a RETURN (docs/api.md, scan modes), so there is no "leave from break".</li>
 *   <li>CLOSE is not allowed on a FREE seat; the closing job only touches claimed
 *       seats, and an unexpected CLOSE points to a bug.</li>
 * </ul>
 */
public final class SeatStateMachine {

    private SeatStateMachine() {
    }

    /**
     * Returns the state the seat moves to.
     *
     * @throws InvalidSeatTransitionException if the event is not allowed from {@code from}
     */
    public static SeatState next(SeatState from, SeatEvent event) {
        SeatState to = switch (from) {
            case FREE -> switch (event) {
                case TAKE -> SeatState.TAKEN;
                case HOLD -> SeatState.HELD;
                case MARK_OUT_OF_SERVICE -> SeatState.OUT_OF_SERVICE;
                default -> null;
            };
            case HELD -> switch (event) {
                case TAKE -> SeatState.TAKEN;
                case RELEASE_HOLD, CLOSE -> SeatState.FREE;
                default -> null;
            };
            case TAKEN -> switch (event) {
                case START_BREAK -> SeatState.ON_BREAK;
                case LEAVE, CLOSE -> SeatState.FREE;
                default -> null;
            };
            case ON_BREAK -> switch (event) {
                case RETURN -> SeatState.TAKEN;
                case BREAK_EXPIRED, CLOSE -> SeatState.FREE;
                default -> null;
            };
            case OUT_OF_SERVICE -> switch (event) {
                case RETURN_TO_SERVICE -> SeatState.FREE;
                default -> null;
            };
        };
        if (to == null) {
            throw new InvalidSeatTransitionException(from, event);
        }
        return to;
    }

    /** True if {@code event} is allowed from {@code from}. */
    public static boolean canApply(SeatState from, SeatEvent event) {
        try {
            next(from, event);
            return true;
        } catch (InvalidSeatTransitionException e) {
            return false;
        }
    }

    /** Every event allowed from {@code from}, for example to decide which buttons the kiosk shows. */
    public static Set<SeatEvent> allowedEvents(SeatState from) {
        Set<SeatEvent> allowed = EnumSet.noneOf(SeatEvent.class);
        for (SeatEvent event : SeatEvent.values()) {
            if (canApply(from, event)) {
                allowed.add(event);
            }
        }
        return allowed;
    }
}
