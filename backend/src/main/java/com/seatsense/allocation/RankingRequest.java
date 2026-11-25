package com.seatsense.allocation;

/**
 * One "find me a seat" question.
 *
 * @param preferences what the student would like (use {@link SeatPreferences#NONE} for none)
 * @param lastSeat    code of the student's previous seat, for "Same seat as last time"; may be null
 * @param nearSeat    code of a seat to stay close to, used to suggest an alternative when the
 *                    seat someone picked was taken a moment ago (409 SEAT_TAKEN); may be null
 */
public record RankingRequest(SeatPreferences preferences, String lastSeat, String nearSeat) {

    public RankingRequest {
        preferences = preferences == null ? SeatPreferences.NONE : preferences;
    }

    public static RankingRequest anySeat() {
        return new RankingRequest(SeatPreferences.NONE, null, null);
    }
}
