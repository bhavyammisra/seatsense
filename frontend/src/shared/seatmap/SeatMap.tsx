import type { Floor, Seat, SeatState, SeatStates } from '../layout'
import './seatmap.css'

type Props = {
  floor: Floor
  states: SeatStates
  selected?: string[]
  /** Seats that cannot be picked are still shown; only FREE seats are clickable. */
  onSeatClick?: (seat: Seat) => void
  /** Read-only maps (public site, dashboard) never react to clicks. */
  readOnly?: boolean
}

const STATE_LABEL: Record<SeatState, string> = {
  FREE: 'free',
  TAKEN: 'taken',
  ON_BREAK: 'on break',
  HELD: 'held for someone',
  OUT_OF_SERVICE: 'out of service',
}

// Cinema-style seat map for one floor. Sections are drawn top to bottom like
// ticket categories, with row letters on both sides and the floor's landmark
// (stairs, entrance) as the "screen" strip at the bottom.
export default function SeatMap({ floor, states, selected = [], onSeatClick, readOnly = false }: Props) {
  const selectedSet = new Set(selected)

  return (
    <div className="seatmap" aria-label={`${floor.name} seat map`}>
      <div className="seatmap-scroll">
        <div className="seatmap-inner" style={{ ['--cols' as string]: floor.gridWidth }}>
          {floor.sections.map((section) => {
            const seats = floor.seats.filter((s) => s.section === section.id)
            const rows = section.toRow - section.fromRow + 1
            const rowLabels = new Map<number, string>()
            for (const s of seats) rowLabels.set(s.gy, s.row)

            return (
              <section key={section.id} className="seatmap-section" aria-label={section.name}>
                <header className="seatmap-section-head">
                  <h3>{section.name}</h3>
                  <p>{section.note}</p>
                </header>
                <div className="seatmap-grid" style={{ gridTemplateRows: `repeat(${rows}, var(--seat))` }}>
                  {[...rowLabels.entries()].map(([gy, letter]) => (
                    <RowLabels key={gy} row={gy - section.fromRow + 1} letter={letter} cols={floor.gridWidth} />
                  ))}
                  {seats.map((seat) => {
                    const state = states[seat.code] ?? 'FREE'
                    const isSelected = selectedSet.has(seat.code)
                    const clickable = !readOnly && state === 'FREE'
                    const label = [
                      `Seat ${seat.code}`,
                      isSelected ? 'selected' : STATE_LABEL[state],
                      seat.power && 'charging point',
                      seat.window && 'window',
                      seat.accessible && 'accessible seat',
                    ]
                      .filter(Boolean)
                      .join(', ')

                    return (
                      <button
                        key={seat.code}
                        type="button"
                        className="seat"
                        data-state={isSelected ? 'SELECTED' : state}
                        data-power={seat.power || undefined}
                        data-accessible={seat.accessible || undefined}
                        style={{ gridColumn: seat.gx + 2, gridRow: seat.gy - section.fromRow + 1 }}
                        aria-label={label}
                        aria-pressed={readOnly ? undefined : isSelected}
                        title={label}
                        disabled={!clickable}
                        onClick={() => clickable && onSeatClick?.(seat)}
                      >
                        {seat.number}
                      </button>
                    )
                  })}
                </div>
              </section>
            )
          })}
          <div className="seatmap-landmark" aria-hidden="true">
            <span>{floor.landmark}</span>
          </div>
        </div>
      </div>
    </div>
  )
}

function RowLabels({ row, letter, cols }: { row: number; letter: string; cols: number }) {
  return (
    <>
      <span className="seatmap-rowlabel" style={{ gridColumn: 1, gridRow: row }}>
        {letter}
      </span>
      <span className="seatmap-rowlabel" style={{ gridColumn: cols + 2, gridRow: row }}>
        {letter}
      </span>
    </>
  )
}

export function SeatLegend({ showSelected = true }: { showSelected?: boolean }) {
  const items: { state: string; label: string }[] = [
    { state: 'FREE', label: 'Free' },
    ...(showSelected ? [{ state: 'SELECTED', label: 'Your pick' }] : []),
    { state: 'TAKEN', label: 'Taken' },
    { state: 'ON_BREAK', label: 'On break' },
    { state: 'HELD', label: 'Held' },
    { state: 'OUT_OF_SERVICE', label: 'Out of service' },
  ]
  return (
    <ul className="seat-legend">
      {items.map((item) => (
        <li key={item.state}>
          <span className="seat seat-swatch" data-state={item.state} aria-hidden="true" />
          {item.label}
        </li>
      ))}
      <li>
        <span className="seat seat-swatch" data-state="FREE" data-power aria-hidden="true" />
        Charging point
      </li>
    </ul>
  )
}
