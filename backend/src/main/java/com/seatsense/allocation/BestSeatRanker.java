package com.seatsense.allocation;

import com.seatsense.session.SeatState;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * "Best seat for me" (docs/PROJECT.md Section 11.1, WORKPLAN S2.1).
 *
 * <p>Every FREE seat gets a score:
 * <pre>
 * score = w1 * preference     share of the student's wishes this seat meets
 *       + w2 * quietness      share of neighbouring seats that are free
 *       - w3 * walking        floors climbed, plus distance from the stairs on that floor
 *       + w4 * sameAsLast     1 if it was the student's previous seat
 *       + w5 * nearness       closeness to a given seat (for "that seat just went, try this one")
 * </pre>
 *
 * <p>Only the best few seats are needed, so instead of sorting every free seat
 * the ranker keeps a {@link PriorityQueue} of at most {@code limit} entries with
 * the WORST of them at the head. Each new seat is offered to the queue, and when
 * the queue grows past {@code limit} the worst is removed. That is O(n log k)
 * for n free seats and k results, instead of O(n log n) for a full sort. The
 * extra seats after the first are the alternatives the gate offers if the best
 * seat is taken by someone else in the same moment.
 *
 * <p>Neighbours of a seat are the seats directly to its left and right on the
 * same floor and row, and at a table also the seat facing it across the table.
 */
public class BestSeatRanker {

    private final RankingWeights weights;

    public BestSeatRanker() {
        this(RankingWeights.DEFAULT);
    }

    public BestSeatRanker(RankingWeights weights) {
        this.weights = weights;
    }

    /**
     * Ranks the free seats.
     *
     * @param seats     every seat in the library (any state)
     * @param states    current state per seat code; a seat missing here counts as FREE
     * @param entrances where the stairs and lift are on each floor, by floor id
     * @param request   what the student wants
     * @param limit     how many seats to return, at least 1
     * @return up to {@code limit} free seats, best first; empty if nothing is free
     */
    public List<ScoredSeat> rank(
        Collection<SeatCandidate> seats,
        Map<String, SeatState> states,
        Map<String, GridPoint> entrances,
        RankingRequest request,
        int limit) {

        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }

        Neighbourhood neighbourhood = new Neighbourhood(seats);
        Map<String, Integer> floorSpan = floorSpans(seats, entrances);
        SeatCandidate near = request.nearSeat() == null ? null : neighbourhood.byCode(request.nearSeat());

        // Worst at the head, so the head is the one to drop when we have too many.
        PriorityQueue<ScoredSeat> best = new PriorityQueue<>(limit + 1, ScoredSeat.BEST_FIRST.reversed());

        for (SeatCandidate seat : seats) {
            if (stateOf(seat, states) != SeatState.FREE) {
                continue;
            }
            best.offer(score(seat, states, entrances, floorSpan, neighbourhood, request, near));
            if (best.size() > limit) {
                best.poll();
            }
        }

        List<ScoredSeat> result = new ArrayList<>(best);
        result.sort(ScoredSeat.BEST_FIRST);
        return result;
    }

    /** The single best free seat, if any. */
    public Optional<ScoredSeat> best(
        Collection<SeatCandidate> seats,
        Map<String, SeatState> states,
        Map<String, GridPoint> entrances,
        RankingRequest request) {
        return rank(seats, states, entrances, request, 1).stream().findFirst();
    }

    ScoredSeat score(
        SeatCandidate seat,
        Map<String, SeatState> states,
        Map<String, GridPoint> entrances,
        Map<String, Integer> floorSpan,
        Neighbourhood neighbourhood,
        RankingRequest request,
        SeatCandidate near) {

        double preference = preferenceMatch(seat, request.preferences());
        double quietness = quietness(seat, states, neighbourhood);
        double walking = walkingCost(seat, entrances, floorSpan);
        double sameAsLast = seat.code().equals(request.lastSeat()) ? 1.0 : 0.0;
        double nearness = nearness(seat, near, floorSpan);

        double score = weights.preference() * preference
            + weights.quietness() * quietness
            - weights.walking() * walking
            + weights.sameAsLast() * sameAsLast
            + weights.nearness() * nearness;

        return new ScoredSeat(seat, score, preference, quietness, walking, sameAsLast, nearness);
    }

    static double preferenceMatch(SeatCandidate seat, SeatPreferences prefs) {
        int asked = prefs.requestedCount();
        if (asked == 0) {
            return 0.0;
        }
        int met = 0;
        if (!prefs.zones().isEmpty() && prefs.zones().contains(seat.zone())) {
            met++;
        }
        if (prefs.wantsPower() && seat.power()) {
            met++;
        }
        if (prefs.wantsWindow() && seat.window()) {
            met++;
        }
        if (prefs.needsAccessible() && seat.accessible()) {
            met++;
        }
        return (double) met / asked;
    }

    static double quietness(SeatCandidate seat, Map<String, SeatState> states, Neighbourhood neighbourhood) {
        List<SeatCandidate> around = neighbourhood.of(seat);
        if (around.isEmpty()) {
            return 1.0;
        }
        long busy = around.stream().filter(n -> stateOf(n, states).isClaimed()).count();
        return 1.0 - (double) busy / around.size();
    }

    double walkingCost(SeatCandidate seat, Map<String, GridPoint> entrances, Map<String, Integer> floorSpan) {
        double climb = weights.floorClimb() * seat.floorNumber();
        GridPoint entrance = entrances.get(seat.floorId());
        if (entrance == null) {
            return climb;
        }
        int span = Math.max(1, floorSpan.getOrDefault(seat.floorId(), 1));
        return climb + (double) entrance.manhattanTo(seat.gx(), seat.gy()) / span;
    }

    static double nearness(SeatCandidate seat, SeatCandidate near, Map<String, Integer> floorSpan) {
        if (near == null || !near.floorId().equals(seat.floorId())) {
            return 0.0;
        }
        int span = Math.max(1, floorSpan.getOrDefault(seat.floorId(), 1));
        int distance = Math.abs(near.gx() - seat.gx()) + Math.abs(near.gy() - seat.gy());
        return Math.max(0.0, 1.0 - (double) distance / span);
    }

    /** The largest walking distance on each floor, used to scale distances to 0..1. */
    static Map<String, Integer> floorSpans(Collection<SeatCandidate> seats, Map<String, GridPoint> entrances) {
        Map<String, Integer> span = new HashMap<>();
        for (SeatCandidate seat : seats) {
            GridPoint entrance = entrances.get(seat.floorId());
            int distance = entrance == null ? 0 : entrance.manhattanTo(seat.gx(), seat.gy());
            span.merge(seat.floorId(), Math.max(1, distance), Math::max);
        }
        return span;
    }

    private static SeatState stateOf(SeatCandidate seat, Map<String, SeatState> states) {
        return states.getOrDefault(seat.code(), SeatState.FREE);
    }

    /** Finds each seat's neighbours in O(1) by indexing seats by floor and grid cell. */
    static final class Neighbourhood {

        private final Map<String, SeatCandidate> byCell = new HashMap<>();
        private final Map<String, SeatCandidate> byCode = new HashMap<>();

        Neighbourhood(Collection<SeatCandidate> seats) {
            for (SeatCandidate seat : seats) {
                byCell.put(key(seat.floorId(), seat.gx(), seat.gy()), seat);
                byCode.put(seat.code(), seat);
            }
        }

        SeatCandidate byCode(String code) {
            return byCode.get(code);
        }

        List<SeatCandidate> of(SeatCandidate seat) {
            List<SeatCandidate> around = new ArrayList<>(3);
            addIfPresent(around, seat.floorId(), seat.gx() - 1, seat.gy());
            addIfPresent(around, seat.floorId(), seat.gx() + 1, seat.gy());
            if (seat.tableId() != null) {
                // The facing seat is two rows away, across the table.
                for (int dy : new int[] {-2, 2}) {
                    SeatCandidate facing = byCell.get(key(seat.floorId(), seat.gx(), seat.gy() + dy));
                    if (facing != null && seat.tableId().equals(facing.tableId())) {
                        around.add(facing);
                    }
                }
            }
            return around;
        }

        private void addIfPresent(List<SeatCandidate> out, String floor, int x, int y) {
            SeatCandidate s = byCell.get(key(floor, x, y));
            if (s != null) {
                out.add(s);
            }
        }

        private static String key(String floor, int x, int y) {
            return floor + ":" + x + ":" + y;
        }
    }
}
