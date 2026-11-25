import type { Floor, Seat, SeatStates } from '../layout'

// Frontend preview of picking several seats together, the way cinema apps do:
// tap one seat and the app fills the rest of your group next to it.
// The real allocation (WORKPLAN S2.3) happens in Java on the server; this only
// decides what to highlight before the request is sent.

const isFree = (seat: Seat, states: SeatStates) => states[seat.code] === 'FREE'

export function pickTogether(floor: Floor, start: Seat, count: number, states: SeatStates): string[] | null {
  if (!isFree(start, states)) return null
  if (count === 1) return [start.code]

  // At a group table, keep everyone at the same table.
  if (start.tableId) {
    const free = floor.seats.filter((s) => s.tableId === start.tableId && isFree(s, states))
    if (free.length < count) return null
    const rest = free.filter((s) => s.code !== start.code).slice(0, count - 1)
    return [start.code, ...rest.map((s) => s.code)]
  }

  // In a row, take adjacent seats without crossing an aisle.
  const row = floor.seats
    .filter((s) => s.row === start.row && s.section === start.section)
    .sort((a, b) => a.gx - b.gx)
  const i = row.findIndex((s) => s.code === start.code)

  // Try windows that contain the tapped seat, preferring it on the left.
  for (let left = i; left > i - count; left--) {
    const window = row.slice(left, left + count)
    if (left < 0 || window.length < count) continue
    const contiguous = window.every((s, k) => k === 0 || s.gx === window[k - 1].gx + 1)
    if (contiguous && window.every((s) => isFree(s, states))) return window.map((s) => s.code)
  }
  return null
}

// Simple preview of "best seat for me": a free seat with a charging point on the
// floor with the most free seats, away from taken neighbours. The real ranking
// is the PriorityQueue in WORKPLAN S2.1.
export function previewBestSeat(floors: Floor[], states: SeatStates): Seat | null {
  let best: { seat: Seat; score: number } | null = null
  for (const floor of floors) {
    const free = floor.seats.filter((s) => isFree(s, states))
    const byCell = new Map(floor.seats.map((s) => [`${s.gx},${s.gy}`, s]))
    for (const seat of free) {
      const neighbours = [
        [seat.gx - 1, seat.gy],
        [seat.gx + 1, seat.gy],
      ]
        .map(([x, y]) => byCell.get(`${x},${y}`))
        .filter((s): s is Seat => Boolean(s))
      const takenNear = neighbours.filter((s) => !isFree(s, states)).length
      const score = free.length / floor.seats.length + (seat.power ? 0.3 : 0) - takenNear * 0.2
      if (!best || score > best.score) best = { seat, score }
    }
  }
  return best?.seat ?? null
}
