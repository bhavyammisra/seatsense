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
  fromRow: number
  toRow: number
}

export type Floor = {
  id: string
  number: number
  name: string
  landmark: string
  gridWidth: number
  gridHeight: number
  sections: Section[]
  seats: Seat[]
}

export type Layout = {
  library: string
  provisional: boolean
  note: string
  totalSeats: number
  floors: Floor[]
}

export type SeatStates = Record<string, SeatState>
