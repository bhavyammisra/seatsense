package com.seatsense.allocation;

import com.seatsense.layout.Zone;

/**
 * What the allocation algorithms need to know about one seat. Built from the
 * layout (docs/layout/) so the algorithms stay plain Java with no database.
 *
 * @param code        seat code, for example {@code 1F-C7}
 * @param floorId     {@code GF}, {@code 1F}, ...
 * @param floorNumber 0 for the ground floor, 1 for the first, ...
 * @param gx          column on the floor grid
 * @param gy          row on the floor grid
 * @param tableId     seats at the same table or in the same room share this; null for counters
 */
public record SeatCandidate(
    String code,
    String floorId,
    int floorNumber,
    int gx,
    int gy,
    Zone zone,
    String tableId,
    boolean power,
    boolean window,
    boolean accessible) {
}
