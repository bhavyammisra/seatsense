package com.seatsense.allocation;

/**
 * How much each part of the score counts (docs/PROJECT.md Section 11.1).
 *
 * @param preference  w1: matching what the student asked for (0 to 1 before weighting)
 * @param quietness   w2: fewer taken neighbours (0 to 1)
 * @param walking     w3: cost of getting there, subtracted (see {@code floorClimb})
 * @param sameAsLast  w4: bonus for the student's previous seat
 * @param nearness    closeness to {@link RankingRequest#nearSeat()} (0 to 1). Heaviest weight on
 *                    purpose: when a student's chosen seat is taken a moment before them, they
 *                    still want to be in that spot, even if it means sitting next to someone
 * @param floorClimb  walking cost of each floor above ground, in the same units as one
 *                    full walk across a floor
 */
public record RankingWeights(
    double preference,
    double quietness,
    double walking,
    double sameAsLast,
    double nearness,
    double floorClimb) {

    public static final RankingWeights DEFAULT = new RankingWeights(3.0, 2.0, 1.0, 2.5, 5.0, 0.25);
}
