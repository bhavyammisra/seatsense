package com.seatsense.session;

import static com.seatsense.session.SeatEvent.BREAK_EXPIRED;
import static com.seatsense.session.SeatEvent.CLOSE;
import static com.seatsense.session.SeatEvent.HOLD;
import static com.seatsense.session.SeatEvent.LEAVE;
import static com.seatsense.session.SeatEvent.MARK_OUT_OF_SERVICE;
import static com.seatsense.session.SeatEvent.RELEASE_HOLD;
import static com.seatsense.session.SeatEvent.RETURN;
import static com.seatsense.session.SeatEvent.RETURN_TO_SERVICE;
import static com.seatsense.session.SeatEvent.START_BREAK;
import static com.seatsense.session.SeatEvent.TAKE;
import static com.seatsense.session.SeatState.FREE;
import static com.seatsense.session.SeatState.HELD;
import static com.seatsense.session.SeatState.ON_BREAK;
import static com.seatsense.session.SeatState.OUT_OF_SERVICE;
import static com.seatsense.session.SeatState.TAKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SeatStateMachineTest {

    /**
     * The allowed moves, written out independently of the implementation.
     * Every pair not listed here must be refused.
     */
    private static final Map<SeatState, Map<SeatEvent, SeatState>> ALLOWED = Map.of(
        FREE, Map.of(
            TAKE, TAKEN,
            HOLD, HELD,
            MARK_OUT_OF_SERVICE, OUT_OF_SERVICE),
        HELD, Map.of(
            TAKE, TAKEN,
            RELEASE_HOLD, FREE,
            CLOSE, FREE),
        TAKEN, Map.of(
            START_BREAK, ON_BREAK,
            LEAVE, FREE,
            CLOSE, FREE),
        ON_BREAK, Map.of(
            RETURN, TAKEN,
            BREAK_EXPIRED, FREE,
            CLOSE, FREE),
        OUT_OF_SERVICE, Map.of(
            RETURN_TO_SERVICE, FREE));

    static Stream<Arguments> everyStateAndEvent() {
        return Stream.of(SeatState.values())
            .flatMap(state -> Stream.of(SeatEvent.values()).map(event -> Arguments.of(state, event)));
    }

    @ParameterizedTest(name = "{0} + {1}")
    @MethodSource("everyStateAndEvent")
    @DisplayName("Every state and event pair is either the listed move or refused")
    void everyPairFollowsTheTable(SeatState from, SeatEvent event) {
        SeatState expected = ALLOWED.get(from).get(event);
        if (expected != null) {
            assertThat(SeatStateMachine.next(from, event)).isEqualTo(expected);
            assertThat(SeatStateMachine.canApply(from, event)).isTrue();
        } else {
            assertThatThrownBy(() -> SeatStateMachine.next(from, event))
                .isInstanceOf(InvalidSeatTransitionException.class)
                .satisfies(e -> {
                    InvalidSeatTransitionException ex = (InvalidSeatTransitionException) e;
                    assertThat(ex.getFrom()).isEqualTo(from);
                    assertThat(ex.getEvent()).isEqualTo(event);
                });
            assertThat(SeatStateMachine.canApply(from, event)).isFalse();
        }
    }

    @Test
    void theTableCoversEveryState() {
        assertThat(ALLOWED.keySet()).containsExactlyInAnyOrder(SeatState.values());
    }

    @Test
    void allowedEventsMatchTheTable() {
        for (SeatState state : SeatState.values()) {
            assertThat(SeatStateMachine.allowedEvents(state))
                .as("allowed events from %s", state)
                .isEqualTo(EnumSet.copyOf(ALLOWED.get(state).keySet()));
        }
    }

    @Test
    @DisplayName("A normal visit: sit, take a break, come back, leave")
    void normalVisit() {
        SeatState seat = FREE;
        seat = SeatStateMachine.next(seat, TAKE);
        seat = SeatStateMachine.next(seat, START_BREAK);
        assertThat(seat).isEqualTo(ON_BREAK);
        seat = SeatStateMachine.next(seat, RETURN);
        seat = SeatStateMachine.next(seat, LEAVE);
        assertThat(seat).isEqualTo(FREE);
    }

    @Test
    @DisplayName("A break that runs out frees the seat for the next person")
    void breakExpires() {
        SeatState seat = SeatStateMachine.next(SeatStateMachine.next(FREE, TAKE), START_BREAK);
        seat = SeatStateMachine.next(seat, BREAK_EXPIRED);
        assertThat(seat).isEqualTo(FREE);
        assertThat(SeatStateMachine.next(seat, TAKE)).isEqualTo(TAKEN);
    }

    @Test
    @DisplayName("Waitlist: hold expires, seat is held for the next person, who takes it")
    void waitlistHandOver() {
        SeatState seat = SeatStateMachine.next(FREE, HOLD);
        seat = SeatStateMachine.next(seat, RELEASE_HOLD);
        seat = SeatStateMachine.next(seat, HOLD);
        seat = SeatStateMachine.next(seat, TAKE);
        assertThat(seat).isEqualTo(TAKEN);
    }

    @Test
    @DisplayName("Closing time clears every claimed seat")
    void closingClearsClaimedSeats() {
        for (SeatState claimed : EnumSet.of(HELD, TAKEN, ON_BREAK)) {
            assertThat(claimed.isClaimed()).isTrue();
            assertThat(SeatStateMachine.next(claimed, CLOSE)).isEqualTo(FREE);
        }
        assertThat(FREE.isClaimed()).isFalse();
        assertThat(OUT_OF_SERVICE.isClaimed()).isFalse();
    }

    @Test
    @DisplayName("Staff cannot take a seat out of use while someone is in it")
    void outOfServiceOnlyWhenFree() {
        assertThat(SeatStateMachine.canApply(TAKEN, MARK_OUT_OF_SERVICE)).isFalse();
        assertThat(SeatStateMachine.canApply(ON_BREAK, MARK_OUT_OF_SERVICE)).isFalse();
        assertThat(SeatStateMachine.canApply(HELD, MARK_OUT_OF_SERVICE)).isFalse();
        SeatState seat = SeatStateMachine.next(FREE, MARK_OUT_OF_SERVICE);
        assertThat(SeatStateMachine.canApply(seat, TAKE)).isFalse();
        assertThat(SeatStateMachine.next(seat, RETURN_TO_SERVICE)).isEqualTo(FREE);
    }

    @Test
    void errorMessageNamesTheStateAndEvent() {
        assertThatThrownBy(() -> SeatStateMachine.next(FREE, START_BREAK))
            .hasMessage("Cannot apply START_BREAK to a seat that is FREE");
    }
}
