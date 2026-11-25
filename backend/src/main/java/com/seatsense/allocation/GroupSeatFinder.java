package com.seatsense.allocation;

import com.seatsense.allocation.GroupOption.Strategy;
import com.seatsense.session.SeatState;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * "Sit together" for 2 to 4 friends (docs/PROJECT.md Section 11.2, WORKPLAN S2.3).
 *
 * <p>Candidate groups come from three searches, in order of how natural they are:
 * <ol>
 *   <li><b>Row:</b> per floor and grid row, a sliding window of k seats over the
 *       seats sorted by column. A window counts only if every seat is FREE and
 *       the columns are consecutive, so a group never spans an aisle.</li>
 *   <li><b>Table:</b> per table or room ({@code tableId}) with at least k FREE
 *       seats, the k free seats closest to each other (smallest total distance
 *       between every pair). Tables hold at most a few dozen seats, so trying
 *       every combination is cheap.</li>
 *   <li><b>Cluster (fallback):</b> a breadth-first search from each FREE seat over
 *       FREE neighbours (side by side, or facing across a table). The first k
 *       seats reached form a connected group.</li>
 * </ol>
 *
 * <p>Every candidate is scored as the average of its seats' best-seat scores
 * ({@link BestSeatRanker}), so groups follow the same preferences as single
 * seats: quiet areas, close to the stairs, the student's wishes. The same set of
 * seats found by two searches is kept once, under the more natural strategy.
 * A bounded {@link PriorityQueue} keeps the best {@code limit} options.
 *
 * <p>This class only suggests groups. Taking the seats, all or nothing in one
 * transaction, is the session service's job.
 */
public class GroupSeatFinder {

    public static final int MIN_GROUP = 2;
    public static final int MAX_GROUP = 4;

    private final BestSeatRanker ranker;

    public GroupSeatFinder() {
        this(new BestSeatRanker());
    }

    public GroupSeatFinder(BestSeatRanker ranker) {
        this.ranker = ranker;
    }

    /**
     * Finds ways to seat {@code groupSize} people together.
     *
     * @return up to {@code limit} options, best first; empty if no group of that size fits anywhere
     */
    public List<GroupOption> find(
        Collection<SeatCandidate> seats,
        Map<String, SeatState> states,
        Map<String, GridPoint> entrances,
        RankingRequest request,
        int groupSize,
        int limit) {

        if (groupSize < MIN_GROUP || groupSize > MAX_GROUP) {
            throw new IllegalArgumentException("Group size must be between " + MIN_GROUP + " and " + MAX_GROUP);
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }

        List<SeatCandidate> free = seats.stream().filter(s -> isFree(s, states)).toList();

        BestSeatRanker.Neighbourhood neighbourhood = new BestSeatRanker.Neighbourhood(seats);
        Map<String, Integer> spans = BestSeatRanker.floorSpans(seats, entrances);
        SeatCandidate near = request.nearSeat() == null ? null : neighbourhood.byCode(request.nearSeat());
        Map<String, Double> seatScore = new HashMap<>();
        for (SeatCandidate seat : free) {
            seatScore.put(seat.code(),
                ranker.score(seat, states, entrances, spans, neighbourhood, request, near).score());
        }

        // Keyed by the set of seat codes so the same group is kept once.
        // Searches run in strategy order, so the first strategy to find a set wins.
        Map<Set<String>, GroupOption> unique = new LinkedHashMap<>();
        Consumer<GroupCandidate> keep = candidate -> unique.putIfAbsent(
            candidate.codes(),
            new GroupOption(inMapOrder(candidate.seats()), average(candidate.seats(), seatScore), candidate.strategy()));

        rowGroups(free, groupSize).forEach(keep);
        tableGroups(free, groupSize).forEach(keep);
        clusterGroups(free, states, neighbourhood, groupSize).forEach(keep);

        PriorityQueue<GroupOption> best = new PriorityQueue<>(limit + 1, GroupOption.BEST_FIRST.reversed());
        for (GroupOption option : unique.values()) {
            best.offer(option);
            if (best.size() > limit) {
                best.poll();
            }
        }
        List<GroupOption> result = new ArrayList<>(best);
        result.sort(GroupOption.BEST_FIRST);
        return result;
    }

    /** Sliding window of k seats per floor and grid row, consecutive columns only. */
    static List<GroupCandidate> rowGroups(List<SeatCandidate> free, int k) {
        Map<String, List<SeatCandidate>> byRow = free.stream()
            .collect(Collectors.groupingBy(s -> s.floorId() + ":" + s.gy()));
        List<GroupCandidate> groups = new ArrayList<>();
        for (List<SeatCandidate> row : byRow.values()) {
            List<SeatCandidate> sorted = row.stream().sorted(Comparator.comparingInt(SeatCandidate::gx)).toList();
            for (int start = 0; start + k <= sorted.size(); start++) {
                List<SeatCandidate> window = sorted.subList(start, start + k);
                if (window.get(k - 1).gx() - window.get(0).gx() == k - 1) {
                    groups.add(new GroupCandidate(window, Strategy.ROW));
                }
            }
        }
        return groups;
    }

    /** Per table with at least k free seats, the k free seats closest together. */
    static List<GroupCandidate> tableGroups(List<SeatCandidate> free, int k) {
        Map<String, List<SeatCandidate>> byTable = free.stream()
            .filter(s -> s.tableId() != null)
            .collect(Collectors.groupingBy(SeatCandidate::tableId));
        List<GroupCandidate> groups = new ArrayList<>();
        for (List<SeatCandidate> table : byTable.values()) {
            if (table.size() < k) {
                continue;
            }
            List<SeatCandidate> sorted = table.stream().sorted(Comparator.comparing(SeatCandidate::code)).toList();
            List<SeatCandidate> closest = closestSubset(sorted, k);
            groups.add(new GroupCandidate(closest, Strategy.TABLE));
        }
        return groups;
    }

    /**
     * Breadth-first search from every free seat over free neighbours. The first k
     * seats reached are connected, so the group can talk to each other.
     */
    static List<GroupCandidate> clusterGroups(
        List<SeatCandidate> free,
        Map<String, SeatState> states,
        BestSeatRanker.Neighbourhood neighbourhood,
        int k) {

        List<GroupCandidate> groups = new ArrayList<>();
        for (SeatCandidate start : free) {
            List<SeatCandidate> reached = new ArrayList<>(k);
            Set<String> seen = new HashSet<>();
            Queue<SeatCandidate> queue = new ArrayDeque<>();
            queue.add(start);
            seen.add(start.code());
            while (!queue.isEmpty() && reached.size() < k) {
                SeatCandidate seat = queue.poll();
                reached.add(seat);
                for (SeatCandidate next : neighbourhood.of(seat)) {
                    if (isFree(next, states) && seen.add(next.code())) {
                        queue.add(next);
                    }
                }
            }
            if (reached.size() == k) {
                groups.add(new GroupCandidate(reached, Strategy.CLUSTER));
            }
        }
        return groups;
    }

    /** The k seats with the smallest total distance between every pair (all combinations). */
    static List<SeatCandidate> closestSubset(List<SeatCandidate> seats, int k) {
        int[] best = null;
        int bestSpread = Integer.MAX_VALUE;
        int[] pick = new int[k];
        for (int i = 0; i < k; i++) {
            pick[i] = i;
        }
        while (true) {
            int spread = 0;
            for (int a = 0; a < k; a++) {
                for (int b = a + 1; b < k; b++) {
                    SeatCandidate s = seats.get(pick[a]);
                    SeatCandidate t = seats.get(pick[b]);
                    spread += Math.abs(s.gx() - t.gx()) + Math.abs(s.gy() - t.gy());
                }
            }
            if (spread < bestSpread) {
                bestSpread = spread;
                best = pick.clone();
            }
            // Next combination in lexicographic order.
            int i = k - 1;
            while (i >= 0 && pick[i] == seats.size() - k + i) {
                i--;
            }
            if (i < 0) {
                break;
            }
            pick[i]++;
            for (int j = i + 1; j < k; j++) {
                pick[j] = pick[j - 1] + 1;
            }
        }
        List<SeatCandidate> chosen = new ArrayList<>(k);
        for (int index : best) {
            chosen.add(seats.get(index));
        }
        return chosen;
    }

    private static double average(List<SeatCandidate> seats, Map<String, Double> seatScore) {
        return seats.stream().mapToDouble(s -> seatScore.get(s.code())).average().orElse(0);
    }

    private static List<SeatCandidate> inMapOrder(List<SeatCandidate> seats) {
        return seats.stream()
            .sorted(Comparator.comparingInt(SeatCandidate::floorNumber)
                .thenComparingInt(SeatCandidate::gy)
                .thenComparingInt(SeatCandidate::gx))
            .toList();
    }

    private static boolean isFree(SeatCandidate seat, Map<String, SeatState> states) {
        return states.getOrDefault(seat.code(), SeatState.FREE) == SeatState.FREE;
    }

    /** A group before scoring. */
    record GroupCandidate(List<SeatCandidate> seats, Strategy strategy) {

        Set<String> codes() {
            return seats.stream().map(SeatCandidate::code).collect(Collectors.toUnmodifiableSet());
        }
    }
}
