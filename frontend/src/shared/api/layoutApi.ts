import type { Fixture, Floor, Section, Seat, SeatState, SeatStates, Zone } from '../layout'

// Client for the public layout and availability API (docs/api.md, "Public").
// Turns the API's floors and seats into the same Floor shape the seat map already draws.

export type FloorAvailability = { id: string; name: string; free: number; total: number; full: boolean }

export type Availability = {
  updatedAt: string
  free: number
  total: number
  full: boolean
  floors: FloorAvailability[]
}

type ApiFloor = {
  id: string
  number: number
  name: string
  short: string
  landmark: string
  gridWidth: number
  gridHeight: number
  sections: Section[]
  fixtures: Fixture[]
}

type ApiSeat = {
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
  state: SeatState
}

/** Thrown when the library system cannot be reached or answers with an error. */
export class ApiUnavailable extends Error {}

async function getJson<T>(url: string): Promise<T> {
  let response: Response
  try {
    response = await fetch(url, { credentials: 'same-origin', headers: { Accept: 'application/json' } })
  } catch {
    throw new ApiUnavailable(`Could not reach ${url}`)
  }
  if (!response.ok) throw new ApiUnavailable(`${url} answered ${response.status}`)
  return (await response.json()) as T
}

/** Every floor with its sections and furniture, without seats. */
export async function fetchFloors(): Promise<ApiFloor[]> {
  return getJson<ApiFloor[]>('/api/public/floors')
}

/** One floor ready for the seat map, plus the current state of every seat on it. */
export async function fetchFloorWithSeats(floor: ApiFloor): Promise<{ floor: Floor; states: SeatStates }> {
  const seats = await getJson<ApiSeat[]>(`/api/public/floors/${encodeURIComponent(floor.id)}/seats`)
  const states: SeatStates = {}
  const mapped: Seat[] = seats.map((s) => {
    states[s.code] = s.state
    return {
      code: s.code,
      floor: s.floor,
      section: s.section,
      zone: s.zone,
      row: s.row,
      number: s.number,
      tableId: s.tableId,
      gx: s.gx,
      gy: s.gy,
      power: s.power,
      window: s.window,
      accessible: s.accessible,
    }
  })
  return {
    floor: {
      id: floor.id,
      number: floor.number,
      name: floor.name,
      short: floor.short,
      landmark: floor.landmark,
      tourScenes: [],
      gridWidth: floor.gridWidth,
      gridHeight: floor.gridHeight,
      sections: floor.sections,
      fixtures: floor.fixtures,
      seats: mapped,
    },
    states,
  }
}

export async function fetchAvailability(): Promise<Availability> {
  return getJson<Availability>('/api/public/availability')
}

export type { ApiFloor }
