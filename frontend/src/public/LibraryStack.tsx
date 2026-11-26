import { useCallback, useEffect, useState } from 'react'
import { fetchFloorWithSeats, type ApiFloor, type Availability } from '../shared/api/layoutApi'
import type { Floor, SeatState, SeatStates } from '../shared/layout'
import { useLive } from '../shared/live/live'

// The hero picture: the library's four floors stacked in isometric view, each
// drawn from its real seat layout. Free seats glow cream, taken ones stay dim,
// and every plate updates live. Clicking a plate opens that floor's map below.

type SeatUpdate = { code: string; state: SeatState; at: string }

const GRID_W = 26 // every floor is 26 cells wide; the deepest is 22
const GRID_H = 22
const S = 11 // pixels per grid cell
const COS = Math.cos(Math.PI / 6)
const SIN = 0.5
const GAP = 104 // vertical distance between floors
const THICK = 8 // slab thickness
const LEFT = GRID_H * COS * S + 4 // x of the plate's top corner
const PLATE_H = (GRID_W + GRID_H) * SIN * S
const LABEL_W = 190

type Props = {
  floors: ApiFloor[]
  availability: Availability
  selected: string | null
  onPick: (floorId: string) => void
}

export default function LibraryStack({ floors, availability, selected, onPick }: Props) {
  const ordered = [...floors].sort((a, b) => a.number - b.number) // ground first
  const levels = ordered.length
  const width = LEFT + GRID_W * COS * S + LABEL_W
  const height = (levels - 1) * GAP + PLATE_H + THICK + 8
  return (
    <figure className="stack">
      <svg viewBox={`0 0 ${Math.ceil(width)} ${Math.ceil(height)}`} className="stack-svg" role="group" aria-label="The library, floor by floor">
        {ordered.map((f, i) => {
          const counts = availability.floors.find((x) => x.id === f.id)
          return (
            <Plate
              key={f.id}
              apiFloor={f}
              index={i}
              top={(levels - 1 - i) * GAP + 4}
              free={counts?.free ?? 0}
              total={counts?.total ?? 0}
              current={f.id === selected}
              onPick={onPick}
            />
          )
        })}
      </svg>
      <figcaption className="stack-key">
        <span className="stack-key-free" aria-hidden="true" /> Free
        <span className="stack-key-taken" aria-hidden="true" /> Taken
        <span className="stack-key-note">Live, from the real floor plans</span>
      </figcaption>
    </figure>
  )
}

function Plate({
  apiFloor,
  index,
  top,
  free,
  total,
  current,
  onPick,
}: {
  apiFloor: ApiFloor
  index: number
  top: number
  free: number
  total: number
  current: boolean
  onPick: (floorId: string) => void
}) {
  const [map, setMap] = useState<{ floor: Floor; states: SeatStates } | null>(null)

  useEffect(() => {
    let cancelled = false
    fetchFloorWithSeats(apiFloor)
      .then((m) => !cancelled && setMap(m))
      .catch(() => undefined)
    return () => {
      cancelled = true
    }
  }, [apiFloor])

  const onSeat = useCallback((u: SeatUpdate) => {
    setMap((m) => (m ? { ...m, states: { ...m.states, [u.code]: u.state } } : m))
  }, [])
  useLive<SeatUpdate>(`/topic/seats/${apiFloor.id}`, onSeat)

  const full = total > 0 && free === 0
  const dy = (GRID_H - apiFloor.gridHeight) / 2 // centre shallower floors on the plate
  const iso = `matrix(${COS * S} ${SIN * S} ${-COS * S} ${SIN * S} ${LEFT} ${top})`
  // Right corner of the plate, where the label hangs.
  const cornerX = LEFT + GRID_W * COS * S
  const cornerY = top + GRID_W * SIN * S
  const label = `${apiFloor.name}: ${full ? 'full' : `${free} of ${total} seats free`}`

  const pick = () => onPick(apiFloor.id)

  return (
    <g
      className="plate"
      data-current={current || undefined}
      role="button"
      tabIndex={0}
      aria-label={`${label}. Show its map.`}
      onClick={pick}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          pick()
        }
      }}
    >
      {/* slab edge, then the floor itself */}
      <g transform={`translate(0 ${THICK})`}>
        <g transform={iso}>
          <rect width={GRID_W} height={GRID_H} className="plate-edge" />
        </g>
      </g>
      <g transform={iso}>
        <rect width={GRID_W} height={GRID_H} className="plate-slab" />
        <g transform={`translate(0 ${dy})`}>
          {apiFloor.fixtures
            .filter((fx) => fx.kind === 'room' || fx.kind === 'area')
            .map((fx, i) => (
              <rect key={`r${i}`} x={fx.gx} y={fx.gy} width={fx.w} height={fx.h} className="plate-room" />
            ))}
          {apiFloor.fixtures
            .filter((fx) => fx.kind === 'table' || fx.kind === 'counter')
            .map((fx, i) => (
              <rect key={`t${i}`} x={fx.gx + 0.1} y={fx.gy + 0.25} width={Math.max(fx.w - 0.2, 0.4)} height={Math.max(fx.h - 0.5, 0.35)} className="plate-table" />
            ))}
          {map?.floor.seats.map((s) => (
            <rect
              key={s.code}
              x={s.gx + 0.18}
              y={s.gy + 0.18}
              width={0.64}
              height={0.64}
              className={`plate-seat seat-${map.states[s.code] ?? 'FREE'}`}
            />
          ))}
        </g>
      </g>
      <line x1={cornerX + 6} y1={cornerY} x2={cornerX + 26} y2={cornerY} className="plate-leader" />
      <text x={cornerX + 34} y={cornerY - 6} className="plate-num">
        {String(index + 1).padStart(2, '0')}
      </text>
      <text x={cornerX + 62} y={cornerY - 6} className="plate-name">
        {apiFloor.name.toUpperCase()}
      </text>
      <text x={cornerX + 34} y={cornerY + 24} className="plate-count">
        {full ? 'Full' : `${free} free`}
      </text>
    </g>
  )
}
