package com.seatsense.layout;

import static org.assertj.core.api.Assertions.assertThat;

import com.seatsense.session.SeatState;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** The seat state rule, without a database. */
class LayoutServiceTest {

    private static Seat seat(String code, boolean outOfService) {
        Seat seat = new Seat();
        ReflectionTestUtils.setField(seat, "code", code);
        ReflectionTestUtils.setField(seat, "outOfService", outOfService);
        return seat;
    }

    @Test
    void aSeatNobodyClaimsIsFree() {
        assertThat(LayoutService.stateOf(seat("1F-A1", false), Map.of())).isEqualTo(SeatState.FREE);
    }

    @Test
    void theSessionModuleDecidesTakenOnBreakAndHeld() {
        Map<String, SeatState> claimed = Map.of(
            "1F-A1", SeatState.TAKEN, "1F-A2", SeatState.ON_BREAK, "1F-A3", SeatState.HELD);

        assertThat(LayoutService.stateOf(seat("1F-A1", false), claimed)).isEqualTo(SeatState.TAKEN);
        assertThat(LayoutService.stateOf(seat("1F-A2", false), claimed)).isEqualTo(SeatState.ON_BREAK);
        assertThat(LayoutService.stateOf(seat("1F-A3", false), claimed)).isEqualTo(SeatState.HELD);
    }

    @Test
    void outOfServiceWinsOverAnythingElse() {
        Map<String, SeatState> claimed = Map.of("1F-A1", SeatState.TAKEN);
        assertThat(LayoutService.stateOf(seat("1F-A1", true), claimed)).isEqualTo(SeatState.OUT_OF_SERVICE);
    }
}
