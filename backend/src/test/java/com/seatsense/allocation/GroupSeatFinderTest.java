package com.seatsense.allocation;

import static com.seatsense.session.SeatState.TAKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seatsense.allocation.GroupOption.Strategy;
import com.seatsense.layout.Zone;
import com.seatsense.session.SeatState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GroupSeatFinderTest {

    private final GroupSeatFinder finder = new GroupSeatFinder();

    private static final Map<String, GridPoint> ENTRANCES = Map.of(
        "GF", new GridPoint(5, 10), "1F", new GridPoint(5, 10), "2F", new GridPoint(5, 10));

    /** Counter seat (no table). */
    private static SeatCandidate desk(String code, String floor, int floorNumber, int gx, int gy) {
        return new SeatCandidate(code, floor, floorNumber, gx, gy, Zone.GENERAL, null, false, false, false);
    }

    private static SeatCandidate desk(String code, int gx, int gy) {
        return desk(code, "1F", 1, gx, gy);
    }

    private static SeatCandidate atTable(String code, int gx, int gy, String table) {
        return new SeatCandidate(code, "1F", 1, gx, gy, Zone.GENERAL, table, false, false, false);
    }

    /** A table drawn like the real ones: perSide seats on top (gy), the table, perSide facing (gy + 2). */
    private static List<SeatCandidate> table(String table, String top, String bottom, int x0, int gy, int perSide) {
        List<SeatCandidate> seats = new ArrayList<>();
        for (int i = 0; i < perSide; i++) {
            seats.add(atTable("1F-" + top + (i + 1), x0 + i, gy, table));
            seats.add(atTable("1F-" + bottom + (i + 1), x0 + i, gy + 2, table));
        }
        return seats;
    }

    private List<GroupOption> find(List<SeatCandidate> seats, Map<String, SeatState> states, int size) {
        return finder.find(seats, states, ENTRANCES, RankingRequest.anySeat(), size, 20);
    }

    private static Set<String> set(String... codes) {
        return Set.of(codes);
    }

    @Test
    @DisplayName("Row: finds side-by-side free seats and skips windows with a taken seat")
    void rowWindowsAreFreeAndConsecutive() {
        List<SeatCandidate> row = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            row.add(desk("1F-A" + (i + 1), i, 4));
        }
        List<GroupOption> options = find(row, Map.of("1F-A2", TAKEN), 3);

        assertThat(options).extracting(o -> Set.copyOf(o.codes()))
            .containsExactlyInAnyOrder(set("1F-A3", "1F-A4", "1F-A5"), set("1F-A4", "1F-A5", "1F-A6"));
        assertThat(options).allSatisfy(o -> assertThat(o.strategy()).isEqualTo(Strategy.ROW));
    }

    @Test
    @DisplayName("Row: a group never spans an aisle")
    void rowGroupsNeverCrossAnAisle() {
        // Columns 0, 1, then an aisle at column 2, then 3, 4.
        List<SeatCandidate> row = List.of(
            desk("1F-A1", 0, 4), desk("1F-A2", 1, 4), desk("1F-A3", 3, 4), desk("1F-A4", 4, 4));

        assertThat(find(row, Map.of(), 3)).isEmpty();
        assertThat(find(row, Map.of(), 2)).extracting(o -> Set.copyOf(o.codes()))
            .containsExactlyInAnyOrder(set("1F-A1", "1F-A2"), set("1F-A3", "1F-A4"));
    }

    @Test
    @DisplayName("Row: seats in the same column on different floors are not a row")
    void rowGroupsStayOnOneFloor() {
        List<SeatCandidate> seats = List.of(desk("GF-A1", "GF", 0, 0, 4), desk("1F-A1", "1F", 1, 1, 4));
        assertThat(find(seats, Map.of(), 2)).isEmpty();
    }

    @Test
    @DisplayName("Table: friends sit at one table even when no row has room")
    void tableKeepsTheGroupAtOneTable() {
        // T1: A1-A4 on top, B1-B4 facing. Every other seat in each row is taken.
        List<SeatCandidate> seats = table("T1", "A", "B", 0, 0, 4);
        Map<String, SeatState> states = Map.of("1F-A2", TAKEN, "1F-A4", TAKEN, "1F-B2", TAKEN, "1F-B4", TAKEN);

        List<GroupOption> options = find(seats, states, 4);

        assertThat(options).singleElement().satisfies(o -> {
            assertThat(o.strategy()).isEqualTo(Strategy.TABLE);
            assertThat(o.codes()).containsExactlyInAnyOrder("1F-A1", "1F-A3", "1F-B1", "1F-B3");
            assertThat(o.seats()).extracting(SeatCandidate::tableId).containsOnly("T1");
        });
    }

    @Test
    @DisplayName("Table: picks the free seats closest together, not just any free seats")
    void tablePicksTheClosestSeats() {
        // Six a side. Free: A1, A2 (left end, side by side), B1 (facing A1) and A6 (far right end).
        List<SeatCandidate> seats = table("T1", "A", "B", 0, 0, 6);
        Map<String, SeatState> states = new HashMap<>();
        for (SeatCandidate s : seats) {
            states.put(s.code(), TAKEN);
        }
        states.remove("1F-A1");
        states.remove("1F-A2");
        states.remove("1F-B1");
        states.remove("1F-A6");

        List<GroupOption> table = find(seats, states, 3).stream()
            .filter(o -> o.strategy() == Strategy.TABLE).toList();

        assertThat(table).singleElement()
            .satisfies(o -> assertThat(o.codes()).containsExactlyInAnyOrder("1F-A1", "1F-A2", "1F-B1"));
    }

    @Test
    @DisplayName("Cluster: breadth-first search returns seats that touch each other")
    void clusterSeatsAreConnected() {
        List<SeatCandidate> seats = table("T1", "A", "B", 0, 0, 3);
        BestSeatRanker.Neighbourhood around = new BestSeatRanker.Neighbourhood(seats);

        List<GroupSeatFinder.GroupCandidate> clusters = GroupSeatFinder.clusterGroups(seats, Map.of(), around, 3);

        assertThat(clusters).isNotEmpty().allSatisfy(c -> {
            assertThat(c.seats()).hasSize(3);
            assertThat(isConnected(c.seats(), around)).as("cluster %s is connected", c.codes()).isTrue();
        });
    }

    @Test
    @DisplayName("Cluster: a taken seat breaks the chain")
    void clusterDoesNotJumpOverTakenSeats() {
        List<SeatCandidate> row = List.of(desk("1F-A1", 0, 4), desk("1F-A2", 1, 4), desk("1F-A3", 2, 4));
        BestSeatRanker.Neighbourhood around = new BestSeatRanker.Neighbourhood(row);
        List<SeatCandidate> free = List.of(row.get(0), row.get(2));

        assertThat(GroupSeatFinder.clusterGroups(free, Map.of("1F-A2", TAKEN), around, 2)).isEmpty();
    }

    @Test
    @DisplayName("The same seats found by two searches appear once, as the more natural one")
    void duplicatesAreKeptOnceUnderTheMoreNaturalStrategy() {
        // A1 and A2 are side by side at one table: row, table and cluster all find them.
        List<SeatCandidate> seats = table("T1", "A", "B", 0, 0, 2);
        Map<String, SeatState> states = Map.of("1F-B1", TAKEN, "1F-B2", TAKEN);

        List<GroupOption> options = find(seats, states, 2);

        assertThat(options).singleElement().satisfies(o -> {
            assertThat(o.codes()).containsExactly("1F-A1", "1F-A2");
            assertThat(o.strategy()).isEqualTo(Strategy.ROW);
        });
    }

    @Test
    @DisplayName("Groups are ranked like single seats: the lower floor wins when all else is equal")
    void betterAreaWins() {
        List<SeatCandidate> seats = List.of(
            desk("2F-A1", "2F", 2, 4, 4), desk("2F-A2", "2F", 2, 5, 4),
            desk("GF-A1", "GF", 0, 4, 4), desk("GF-A2", "GF", 0, 5, 4));

        List<GroupOption> options = find(seats, Map.of(), 2);

        assertThat(options.get(0).codes()).containsExactly("GF-A1", "GF-A2");
        assertThat(options.get(0).score()).isGreaterThan(options.get(1).score());
    }

    @Test
    @DisplayName("Each option lists its seats in map order")
    void seatsComeInMapOrder() {
        List<SeatCandidate> seats = table("T1", "A", "B", 0, 0, 2);
        GroupOption option = find(seats, Map.of(), 4).get(0);
        assertThat(option.codes()).containsExactly("1F-A1", "1F-A2", "1F-B1", "1F-B2");
    }

    @Test
    void nothingFitsMeansNoOptions() {
        List<SeatCandidate> seats = List.of(desk("1F-A1", 0, 4), desk("1F-A2", 1, 4));
        assertThat(find(seats, Map.of("1F-A1", TAKEN), 2)).isEmpty();
    }

    @Test
    void groupSizeMustBeTwoToFour() {
        List<SeatCandidate> seats = List.of(desk("1F-A1", 0, 4));
        assertThatThrownBy(() -> find(seats, Map.of(), 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> find(seats, Map.of(), 5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Random library: every option is free, the right size, and together")
    void everyOptionIsValidOnARandomLibrary() {
        Random random = new Random(20261125L);
        List<SeatCandidate> seats = new ArrayList<>();
        Map<String, SeatState> states = new HashMap<>();
        for (int t = 0; t < 12; t++) {
            String tableId = "T" + t;
            int x0 = (t % 3) * 8;
            int y0 = (t / 3) * 4;
            for (int i = 0; i < 6; i++) {
                for (int side : new int[] {0, 2}) {
                    String code = "1F-" + tableId + (side == 0 ? "N" : "S") + (i + 1);
                    seats.add(atTable(code, x0 + i, y0 + side, tableId));
                    if (random.nextInt(10) < 6) {
                        states.put(code, TAKEN);
                    }
                }
            }
        }
        BestSeatRanker.Neighbourhood around = new BestSeatRanker.Neighbourhood(seats);

        for (int size = GroupSeatFinder.MIN_GROUP; size <= GroupSeatFinder.MAX_GROUP; size++) {
            List<GroupOption> options = finder.find(seats, states, ENTRANCES, RankingRequest.anySeat(), size, 50);
            int k = size;
            assertThat(options).allSatisfy(o -> {
                assertThat(o.seats()).hasSize(k);
                assertThat(new HashSet<>(o.codes())).hasSize(k);
                assertThat(o.seats()).allSatisfy(s -> assertThat(states.get(s.code())).isNull());
                boolean oneTable = o.seats().stream().map(SeatCandidate::tableId).distinct().count() == 1;
                assertThat(oneTable || isConnected(o.seats(), around)).isTrue();
            });
            for (int i = 1; i < options.size(); i++) {
                assertThat(options.get(i - 1).score()).isGreaterThanOrEqualTo(options.get(i).score());
            }
        }
    }

    private static boolean isConnected(List<SeatCandidate> group, BestSeatRanker.Neighbourhood around) {
        Set<String> members = new HashSet<>();
        group.forEach(s -> members.add(s.code()));
        Set<String> seen = new HashSet<>();
        List<SeatCandidate> stack = new ArrayList<>(List.of(group.get(0)));
        seen.add(group.get(0).code());
        while (!stack.isEmpty()) {
            SeatCandidate seat = stack.remove(stack.size() - 1);
            for (SeatCandidate next : around.of(seat)) {
                if (members.contains(next.code()) && seen.add(next.code())) {
                    stack.add(next);
                }
            }
        }
        return seen.size() == group.size();
    }
}
