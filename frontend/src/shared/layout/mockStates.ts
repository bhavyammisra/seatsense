import type { Layout, SeatState, SeatStates } from './types'

// SAMPLE seat states for building screens before the backend exists.
// Deterministic, so the same seats are taken on every reload.
// Upper floors are fuller, like an exam-season evening.

function hash(text: string): number {
  let h = 2166136261
  for (let i = 0; i < text.length; i++) {
    h ^= text.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  // Final mix so codes that differ only in the last character spread out.
  h ^= h >>> 16
  h = Math.imul(h, 0x85ebca6b)
  h ^= h >>> 13
  h = Math.imul(h, 0xc2b2ae35)
  h ^= h >>> 16
  return (h >>> 0) / 4294967295
}

const FULLNESS: Record<string, number> = {
  GF: 0.5,
  '1F': 0.68,
  '2F': 0.78,
  '3F': 0.93,
  '4F': 1,
}

export function mockSeatStates(layout: Layout): SeatStates {
  const states: SeatStates = {}
  for (const floor of layout.floors) {
    const fullness = FULLNESS[floor.id] ?? 0.8
    for (const seat of floor.seats) {
      const r = hash(seat.code)
      let state: SeatState = r < fullness ? 'TAKEN' : 'FREE'
      if (state === 'TAKEN' && r > fullness - 0.04) state = 'ON_BREAK'
      if (state === 'FREE' && r > 0.985) state = 'HELD'
      if (seat.code.endsWith('-C14') || seat.code.endsWith('-D3')) state = 'OUT_OF_SERVICE'
      states[seat.code] = state
    }
  }
  return states
}

export function countFree(seatCodes: string[], states: SeatStates): number {
  return seatCodes.reduce((n, code) => n + (states[code] === 'FREE' ? 1 : 0), 0)
}
