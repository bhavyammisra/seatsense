// Shapes shared by every screen that draws the library.
// They mirror the layout JSON (docs/layout/) and the seat states in
// docs/PROJECT.md Section 9.

export type Zone = 'QUIET' | 'GENERAL' | 'DISCUSSION'

export type SeatState = 'FREE' | 'TAKEN' | 'ON_BREAK' | 'HELD' | 'OUT_OF_SERVICE'

export type Seat = {
  code: string
  floor: string
  section: string
  zone: Zone
  row: string
  number: number
  tableId: string | null
  gx: number
  gy: number
  power: boolean
  window: boolean
  accessible: boolean
}

export type Section = {
  id: string
  name: string
  zone: Zone
  note: string
  /** False for areas that are drawn but cannot be booked (seminar hall, lounge). */
  bookable: boolean
  fromRow: number
  toRow: number
}

/** Furniture and rooms drawn on the map. Not clickable. */
export type Fixture = {
  kind: 'table' | 'counter' | 'room' | 'area'
  section: string
  gx: number
  gy: number
  w: number
  h: number
  label: string
}

export type Floor = {
  id: string
  number: number
  name: string
  short: string
  landmark: string
  /** Scene labels in the library's 360 walkthrough that show this floor. */
  tourScenes: string[]
  gridWidth: number
  gridHeight: number
  sections: Section[]
  fixtures: Fixture[]
  seats: Seat[]
}

export type Layout = {
  library: string
  source: string
  provisional: boolean
  note: string
  totalSeats: number
  floors: Floor[]
}

export type SeatStates = Record<string, SeatState>
