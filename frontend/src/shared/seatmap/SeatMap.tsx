import type { Fixture, Floor, Seat, SeatState, SeatStates } from '../layout'
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

// Seat map for one floor, laid out like a cinema booking screen: each area of
// the floor is a "category" drawn top to bottom, with its real furniture
// (long tables, counters along the atrium rail, glass rooms) and the floor's
// fixed landmark (stairs, atrium) as the "screen" strip at the bottom.
export default function SeatMap({ floor, states, selected = [], onSeatClick, readOnly = false }: Props) {
  const selectedSet = new Set(selected)

  return (
    <div className="seatmap" aria-label={`${floor.name} seat map`}>
      <div className="seatmap-scroll">
        <div className="seatmap-inner">
          {floor.sections.map((section) => {
            const seats = floor.seats.filter((s) => s.section === section.id)
            const fixtures = floor.fixtures.filter((f) => f.section === section.id)
            const rows = section.toRow - section.fromRow + 1
            const rowY = (gy: number) => gy - section.fromRow + 1

            const rowLabels = new Map<number, string>()
            for (const s of seats) rowLabels.set(s.gy, s.row)
            const free = seats.filter((s) => (states[s.code] ?? 'FREE') === 'FREE').length

            return (
              <section
                key={section.id}
                className="seatmap-section"
                data-bookable={section.bookable || undefined}
                aria-label={section.name}
              >
                <header className="seatmap-section-head">
                  <h3>{section.name}</h3>
                  <p>{section.note}</p>
                  {section.bookable && (
                    <span className="seatmap-section-free" data-none={free === 0 || undefined}>
                      {free === 0 ? 'Full' : `${free} free`}
                    </span>
                  )}
                </header>
                <div
                  className="seatmap-grid"
                  style={{
                    gridTemplateColumns: `var(--label) repeat(${floor.gridWidth}, var(--seat)) var(--label)`,
                    gridTemplateRows: `repeat(${rows}, var(--seat))`,
                  }}
                >
                  {fixtures.map((f, i) => (
                    <FixtureBox key={i} fixture={f} row={rowY(f.gy)} />
                  ))}
                  {[...rowLabels.entries()].map(([gy, letter]) => (
                    <RowLabels key={gy} row={rowY(gy)} letter={letter} cols={floor.gridWidth} />
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
                        style={{ gridColumn: seat.gx + 2, gridRow: rowY(seat.gy) }}
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

function FixtureBox({ fixture, row }: { fixture: Fixture; row: number }) {
  return (
    <div
      className="fixture"
      data-kind={fixture.kind}
      style={{
        gridColumn: `${fixture.gx + 2} / span ${fixture.w}`,
        gridRow: `${row} / span ${fixture.h}`,
      }}
      aria-hidden="true"
    >
      {fixture.label && <span>{fixture.label}</span>}
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
