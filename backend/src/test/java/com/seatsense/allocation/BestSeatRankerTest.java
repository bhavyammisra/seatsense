package com.seatsense.allocation;

import static com.seatsense.session.SeatState.FREE;
import static com.seatsense.session.SeatState.HELD;
import static com.seatsense.session.SeatState.ON_BREAK;
import static com.seatsense.session.SeatState.OUT_OF_SERVICE;
import static com.seatsense.session.SeatState.TAKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seatsense.layout.Zone;
import com.seatsense.session.SeatState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BestSeatRankerTest {

    private final BestSeatRanker ranker = new BestSeatRanker();

    // Stairs at the bottom middle of every test floor.
    private static final Map<String, GridPoint> ENTRANCES = Map.of(
        "GF", new GridPoint(5, 10),
        "1F", new GridPoint(5, 10),
        "2F", new GridPoint(5, 10));

    private static SeatCandidate seat(String code, String floor, int floorNumber, int gx, int gy) {
        return new SeatCandidate(code, floor, floorNumber, gx, gy, Zone.GENERAL, null, false, false, false);
    }

    private static SeatCandidate seat(String code, int gx, int gy, Zone zone, boolean power, boolean window) {
        return new SeatCandidate(code, "1F", 1, gx, gy, zone, null, power, window, false);
    }

    private static SeatCandidate tableSeat(String code, int gx, int gy, String tableId) {
        return new SeatCandidate(code, "1F", 1, gx, gy, Zone.GENERAL, tableId, false, false, false);
    }

    private static List<String> codes(List<ScoredSeat> ranked) {
        return ranked.stream().map(s -> s.seat().code()).toList();
    }

    @Test
    void returnsNothingWhenNoSeatIsFree() {
        List<SeatCandidate> seats = List.of(seat("1F-A1", "1F", 1, 0, 0), seat("1F-A2", "1F", 1, 1, 0));
        Map<String, SeatState> states = Map.of("1F-A1", TAKEN, "1F-A2", OUT_OF_SERVICE);

        assertThat(ranker.rank(seats, states, ENTRANCES, RankingRequest.anySeat(), 5)).isEmpty();
        assertThat(ranker.best(seats, states, ENTRANCES, RankingRequest.anySeat())).isEmpty();
    }

    @Test
    void onlyFreeSeatsAreSuggestedAndTheLimitIsKept() {
        List<SeatCandidate> seats = new ArrayList<>();
        Map<String, SeatState> states = new HashMap<>();
        SeatState[] cycle = {FREE, TAKEN, ON_BREAK, HELD, OUT_OF_SERVICE};
        for (int i = 0; i < 20; i++) {
            String code = "1F-A" + (i + 1);
            seats.add(seat(code, "1F", 1, i, 0));
            states.put(code, cycle[i % cycle.length]);
        }

        List<ScoredSeat> ranked = ranker.rank(seats, states, ENTRANCES, RankingRequest.anySeat(), 3);

        assertThat(ranked).hasSize(3);
        assertThat(ranked).allSatisfy(s -> assertThat(states.get(s.seat().code())).isEqualTo(FREE));
    }

    @Test
    @DisplayName("A seat missing from the states map counts as free")
    void missingStateMeansFree() {
        List<SeatCandidate> seats = List.of(seat("1F-A1", "1F", 1, 0, 0));
        assertThat(ranker.best(seats, Map.of(), ENTRANCES, RankingRequest.anySeat())).isPresent();
    }

    @Test
    @DisplayName("Asked for a charging point: the seat with power wins")
    void prefersPowerWhenAsked() {
        List<SeatCandidate> seats = List.of(
            seat("1F-A1", 4, 5, Zone.GENERAL, false, false),
            seat("1F-B1", 6, 5, Zone.GENERAL, true, false));
        RankingRequest wantsPower =
            new RankingRequest(new SeatPreferences(Set.of(), true, false, false), null, null);

        ScoredSeat best = ranker.best(seats, Map.of(), ENTRANCES, wantsPower).orElseThrow();

        assertThat(best.seat().code()).isEqualTo("1F-B1");
        assertThat(best.preference()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Asked for the quiet zone: a quiet seat beats a closer general one")
    void prefersRequestedZone() {
        List<SeatCandidate> seats = List.of(
            seat("1F-A1", 5, 9, Zone.GENERAL, false, false),
            seat("1F-B1", 0, 0, Zone.QUIET, false, false));
        RankingRequest wantsQuiet =
            new RankingRequest(new SeatPreferences(Set.of(Zone.QUIET), false, false, false), null, null);

        assertThat(ranker.best(seats, Map.of(), ENTRANCES, wantsQuiet).orElseThrow().seat().code())
            .isEqualTo("1F-B1");
    }

    @Test
    @DisplayName("Spreads people out: a seat with free neighbours beats one squeezed between two people")
    void prefersQuietNeighbourhood() {
        // Row A, seats 1 to 5 side by side. A1 and A3 are taken.
        List<SeatCandidate> seats = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            seats.add(seat("1F-A" + (i + 1), "1F", 1, i, 5));
        }
        Map<String, SeatState> states = Map.of("1F-A1", TAKEN, "1F-A3", TAKEN);

        List<ScoredSeat> ranked = ranker.rank(seats, states, ENTRANCES, RankingRequest.anySeat(), 3);

        // A5: its only neighbour A4 is free. A4: one of two neighbours taken. A2: both taken.
        assertThat(codes(ranked)).containsExactly("1F-A5", "1F-A4", "1F-A2");
        assertThat(ranked.get(0).quietness()).isEqualTo(1.0);
        assertThat(ranked.get(1).quietness()).isEqualTo(0.5);
        assertThat(ranked.get(2).quietness()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("At a table, the person sitting opposite counts as a neighbour")
    void facingSeatAcrossTableCountsAsNeighbour() {
        // Table T1: A1 on top, B1 facing it two rows down. C1 is a lone desk.
        List<SeatCandidate> seats = List.of(
            tableSeat("1F-A1", 2, 0, "T1"),
            tableSeat("1F-B1", 2, 2, "T1"),
            seat("1F-C1", "1F", 1, 8, 0));
        Map<String, SeatState> states = Map.of("1F-B1", TAKEN);

        BestSeatRanker.Neighbourhood around = new BestSeatRanker.Neighbourhood(seats);
        assertThat(around.of(seats.get(0))).extracting(SeatCandidate::code).containsExactly("1F-B1");
        assertThat(BestSeatRanker.quietness(seats.get(0), states, around)).isEqualTo(0.0);
        assertThat(BestSeatRanker.quietness(seats.get(2), states, around)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Seats on different tables are not neighbours, even two rows apart")
    void seatsAtDifferentTablesAreNotNeighbours() {
        List<SeatCandidate> seats = List.of(tableSeat("1F-A1", 2, 0, "T1"), tableSeat("1F-C1", 2, 2, "T2"));
        assertThat(new BestSeatRanker.Neighbourhood(seats).of(seats.get(0))).isEmpty();
    }

    @Test
    @DisplayName("All else equal, the lower floor wins (fewer stairs)")
    void lowerFloorWinsWhenOtherwiseEqual() {
        List<SeatCandidate> seats = List.of(seat("2F-A1", "2F", 2, 5, 5), seat("GF-A1", "GF", 0, 5, 5));

        assertThat(codes(ranker.rank(seats, Map.of(), ENTRANCES, RankingRequest.anySeat(), 2)))
            .containsExactly("GF-A1", "2F-A1");
    }

    @Test
    @DisplayName("All else equal, the seat closer to the stairs wins")
    void closerToStairsWins() {
        List<SeatCandidate> seats = List.of(seat("1F-A1", "1F", 1, 0, 0), seat("1F-J1", "1F", 1, 5, 9));

        assertThat(ranker.best(seats, Map.of(), ENTRANCES, RankingRequest.anySeat()).orElseThrow().seat().code())
            .isEqualTo("1F-J1");
    }

    @Test
    @DisplayName("Same seat as last time wins when it is free")
    void lastSeatWinsWhenFree() {
        List<SeatCandidate> seats = List.of(seat("1F-A1", "1F", 1, 0, 0), seat("1F-J1", "1F", 1, 5, 9));
        RankingRequest sameAsLast = new RankingRequest(SeatPreferences.NONE, "1F-A1", null);

        ScoredSeat best = ranker.best(seats, Map.of(), ENTRANCES, sameAsLast).orElseThrow();

        assertThat(best.seat().code()).isEqualTo("1F-A1");
        assertThat(best.sameAsLast()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Same seat as last time is skipped when someone else has it")
    void lastSeatIgnoredWhenTaken() {
        List<SeatCandidate> seats = List.of(seat("1F-A1", "1F", 1, 0, 0), seat("1F-J1", "1F", 1, 5, 9));
        RankingRequest sameAsLast = new RankingRequest(SeatPreferences.NONE, "1F-A1", null);

        assertThat(codes(ranker.rank(seats, Map.of("1F-A1", TAKEN), ENTRANCES, sameAsLast, 5)))
            .containsExactly("1F-J1");
    }

    @Test
    @DisplayName("Seat just taken by someone else: the alternative is the nearest free seat")
    void alternativeStaysNearTheSeatThatWasTaken() {
        // 1F-A1 was picked but is now taken. A2 is next to it; J1 is near the stairs.
        List<SeatCandidate> seats = List.of(
            seat("1F-A1", "1F", 1, 0, 0),
            seat("1F-A2", "1F", 1, 1, 0),
            seat("1F-J1", "1F", 1, 5, 9));
        RankingRequest nearA1 = new RankingRequest(SeatPreferences.NONE, null, "1F-A1");

        ScoredSeat alternative = ranker.best(seats, Map.of("1F-A1", TAKEN), ENTRANCES, nearA1).orElseThrow();

        assertThat(alternative.seat().code()).isEqualTo("1F-A2");
        assertThat(alternative.nearness()).isGreaterThan(0.8);
    }

    @Test
    @DisplayName("Equal scores always come out in the same order")
    void tiesAreBrokenByFloorThenCode() {
        // Mirror images around the stairs: identical scores.
        List<SeatCandidate> seats = List.of(seat("1F-A9", "1F", 1, 7, 5), seat("1F-A1", "1F", 1, 3, 5));

        assertThat(codes(ranker.rank(seats, Map.of(), ENTRANCES, RankingRequest.anySeat(), 2)))
            .containsExactly("1F-A1", "1F-A9");
    }

    @Test
    @DisplayName("The bounded heap gives exactly the same top 10 as sorting every free seat")
    void heapMatchesFullSortOnARandomLibrary() {
        Random random = new Random(20261125L);
        Zone[] zones = Zone.values();
        SeatState[] stateChoices = {FREE, FREE, TAKEN, TAKEN, TAKEN, ON_BREAK, HELD, OUT_OF_SERVICE};
        List<SeatCandidate> seats = new ArrayList<>();
        Map<String, SeatState> states = new HashMap<>();
        String[] floors = {"GF", "1F", "2F"};
        for (int f = 0; f < floors.length; f++) {
            for (int y = 0; y < 10; y++) {
                for (int x = 0; x < 20; x++) {
                    String code = floors[f] + "-R" + y + "S" + x;
                    String table = y % 3 == 2 ? null : floors[f] + "-T" + (y / 3) + "-" + (x / 4);
                    seats.add(new SeatCandidate(code, floors[f], f, x, y, zones[random.nextInt(zones.length)],
                        table, random.nextBoolean(), random.nextInt(5) == 0, random.nextInt(20) == 0));
                    states.put(code, stateChoices[random.nextInt(stateChoices.length)]);
                }
            }
        }
        RankingRequest request = new RankingRequest(
            new SeatPreferences(Set.of(Zone.QUIET), true, false, false), "1F-R3S3", "2F-R5S10");

        List<ScoredSeat> fromHeap = ranker.rank(seats, states, ENTRANCES, request, 10);

        BestSeatRanker.Neighbourhood around = new BestSeatRanker.Neighbourhood(seats);
        Map<String, Integer> spans = BestSeatRanker.floorSpans(seats, ENTRANCES);
        SeatCandidate near = around.byCode(request.nearSeat());
        List<ScoredSeat> everyFree = new ArrayList<>(seats.stream()
            .filter(s -> states.get(s.code()) == FREE)
            .map(s -> ranker.score(s, states, ENTRANCES, spans, around, request, near))
            .toList());
        everyFree.sort(ScoredSeat.BEST_FIRST);

        assertThat(codes(fromHeap)).containsExactlyElementsOf(codes(everyFree.subList(0, 10)));
    }

    @Test
    void limitMustBePositive() {
        assertThatThrownBy(() -> ranker.rank(List.of(), Map.of(), ENTRANCES, RankingRequest.anySeat(), 0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
