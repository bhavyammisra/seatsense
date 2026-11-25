package com.seatsense.allocation;

import java.util.Comparator;

/**
 * A free seat and its score. The parts are kept so the kiosk can say why a
 * seat was picked ("quiet spot, charging point, close to the stairs").
 *
 * @param score      total: weighted sum of the parts below
 * @param preference 0 to 1, share of the student's preferences this seat meets
 * @param quietness  0 to 1, share of neighbouring seats that are free
 * @param walking    0 and up, floors climbed plus distance from the stairs on the floor
 * @param sameAsLast 1 if this was the student's previous seat, else 0
 * @param nearness   0 to 1, closeness to the requested nearby seat, else 0
 */
public record ScoredSeat(
    SeatCandidate seat,
    double score,
    double preference,
    double quietness,
    double walking,
    double sameAsLast,
    double nearness) {

    /**
     * Best first: higher score, then lower floor, then seat code, so equal
     * scores always come out in the same order.
     */
    public static final Comparator<ScoredSeat> BEST_FIRST = Comparator
        .comparingDouble(ScoredSeat::score).reversed()
        .thenComparingInt(s -> s.seat().floorNumber())
        .thenComparing(s -> s.seat().code());
}
