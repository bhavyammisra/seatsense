package com.seatsense.allocation;

import java.util.Comparator;
import java.util.List;

/**
 * One way to seat a group together.
 *
 * @param seats    the seats, in map order (top to bottom, then left to right)
 * @param score    average best-seat score of the seats (docs/PROJECT.md Section 11.1)
 * @param strategy how the seats were found
 */
public record GroupOption(List<SeatCandidate> seats, double score, Strategy strategy) {

    /** How a group of seats was found, in order of preference. */
    public enum Strategy {
        /** Side by side in one row, no aisle in between. */
        ROW,
        /** At the same table or in the same room. */
        TABLE,
        /** Seats that touch each other (side by side or facing), found by a breadth-first search. */
        CLUSTER
    }

    public GroupOption {
        seats = List.copyOf(seats);
    }

    /** Seat codes, for the API and for logging. */
    public List<String> codes() {
        return seats.stream().map(SeatCandidate::code).toList();
    }

    /**
     * Best first: higher score, then the more natural way of sitting together
     * (row, then table, then cluster), then the first seat code, so equal
     * options always come out in the same order.
     */
    public static final Comparator<GroupOption> BEST_FIRST = Comparator
        .comparingDouble(GroupOption::score).reversed()
        .thenComparing(GroupOption::strategy)
        .thenComparing(o -> o.seats().get(0).code());
}
