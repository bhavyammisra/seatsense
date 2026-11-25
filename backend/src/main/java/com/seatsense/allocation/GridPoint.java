package com.seatsense.allocation;

/** A cell on a floor's seat grid. Used for each floor's entrance (stairs and lift). */
public record GridPoint(int x, int y) {

    public int manhattanTo(int otherX, int otherY) {
        return Math.abs(x - otherX) + Math.abs(y - otherY);
    }
}
