import { useMemo, useState } from 'react'
import { provisionalLayout, type Seat } from '../shared/layout'
import { countFree, mockSeatStates } from '../shared/layout/mockStates'
import SeatMap, { SeatLegend } from '../shared/seatmap/SeatMap'
import { pickTogether, previewBestSeat } from '../shared/seatmap/selection'
import './kiosk.css'

// Gate kiosk, seat-picking step. Runs on SAMPLE data until the gate and
// session APIs exist (WORKPLAN S1.5); then the scan supplies the student and
// "Confirm" calls POST /api/sessions.

const SAMPLE_STUDENT = { name: 'Aarav S.', initials: 'AS', cardId: 'SAMPLE-102301' }
const GROUP_SIZES = [1, 2, 3, 4]

type Step = { kind: 'pick' } | { kind: 'done'; codes: string[]; floorName: string; sectionName: string }

export default function KioskPage() {
  const layout = provisionalLayout
  const [states, setStates] = useState(() => mockSeatStates(layout))
  const [floorId, setFloorId] = useState('2F')
  const [groupSize, setGroupSize] = useState(1)
  const [selected, setSelected] = useState<string[]>([])
  const [notice, setNotice] = useState<string | null>(null)
  const [step, setStep] = useState<Step>({ kind: 'pick' })

  const floor = layout.floors.find((f) => f.id === floorId)!
  const freeByFloor = useMemo(
    () => Object.fromEntries(layout.floors.map((f) => [f.id, countFree(f.seats.map((s) => s.code), states)])),
    [layout, states],
  )
  const totalFree = Object.values(freeByFloor).reduce((a, b) => a + b, 0)
  const selectedSeats = selected
    .map((code) => layout.floors.flatMap((f) => f.seats).find((s) => s.code === code))
    .filter((s): s is Seat => Boolean(s))

  function changeFloor(id: string) {
    setFloorId(id)
    setSelected([])
    setNotice(null)
  }

  function changeGroupSize(n: number) {
    setGroupSize(n)
    setSelected([])
    setNotice(null)
  }

  function handleSeatClick(seat: Seat) {
    if (selected.includes(seat.code)) {
      setSelected([])
      return
    }
    const pick = pickTogether(floor, seat, groupSize, states)
    if (pick) {
      setSelected(pick)
      setNotice(null)
    } else {
      setNotice(`No ${groupSize} free seats together there. Try another row or a group table.`)
    }
  }

  function handleBestSeat() {
    const seat = previewBestSeat(layout.floors, states)
    if (!seat) return
    setFloorId(seat.floor)
    setSelected([seat.code])
    setGroupSize(1)
    setNotice(null)
  }

  function handleConfirm() {
    const first = selectedSeats[0]
    if (!first) return
    const f = layout.floors.find((x) => x.id === first.floor)!
    setStates((prev) => ({ ...prev, ...Object.fromEntries(selected.map((c) => [c, 'TAKEN'])) }))
    setStep({
      kind: 'done',
      codes: selected,
      floorName: f.name,
      sectionName: f.sections.find((s) => s.id === first.section)?.name ?? '',
    })
    setSelected([])
  }

  if (step.kind === 'done') {
    return (
      <KioskShell>
        <div className="kiosk-done">
          <div className="ticket">
            <p className="ticket-label">{step.codes.length > 1 ? 'Your seats' : 'Your seat'}</p>
            <p className="ticket-code mono">{step.codes.join('  ')}</p>
            <dl className="ticket-meta">
              <div>
                <dt>Floor</dt>
                <dd>{step.floorName}</dd>
              </div>
              <div>
                <dt>Area</dt>
                <dd>{step.sectionName}</dd>
              </div>
              <div>
                <dt>Name</dt>
                <dd>{SAMPLE_STUDENT.name}</dd>
              </div>
            </dl>
            <p className="ticket-foot">Scan your ID at the gate when you leave. Choose Break if you are coming back within 30 minutes.</p>
          </div>
          <button type="button" className="btn btn-primary" onClick={() => setStep({ kind: 'pick' })}>
            Done
          </button>
        </div>
      </KioskShell>
    )
  }

  return (
    <KioskShell>
      <div className="kiosk-top">
        <div className="kiosk-welcome">
          <span className="avatar" aria-hidden="true">
            {SAMPLE_STUDENT.initials}
          </span>
          <div>
            <h1>Welcome, {SAMPLE_STUDENT.name}</h1>
            <p className="muted">
              {totalFree} of {layout.totalSeats} seats free right now
            </p>
          </div>
        </div>

        <fieldset className="group-size">
          <legend>How many seats?</legend>
          <div className="group-size-options">
            {GROUP_SIZES.map((n) => (
              <button
                key={n}
                type="button"
                className="group-size-option"
                aria-pressed={groupSize === n}
                onClick={() => changeGroupSize(n)}
              >
                {n}
              </button>
            ))}
          </div>
        </fieldset>
      </div>

      <nav className="floor-tabs" aria-label="Floors">
        {layout.floors.map((f) => {
          const free = freeByFloor[f.id]
          return (
            <button
              key={f.id}
              type="button"
              className="floor-tab"
              aria-current={f.id === floorId ? 'true' : undefined}
              data-full={free === 0 || undefined}
              onClick={() => changeFloor(f.id)}
            >
              <span className="floor-tab-name">{f.name}</span>
              <span className="floor-tab-free">{free === 0 ? 'Full' : `${free} free`}</span>
            </button>
          )
        })}
      </nav>

      <div className="kiosk-map card">
        {freeByFloor[floorId] === 0 && (
          <p className="floor-full-note">
            {floor.name} is full. Pick another floor, or tap Best seat for me.
          </p>
        )}
        <SeatMap floor={floor} states={states} selected={selected} onSeatClick={handleSeatClick} />
        <SeatLegend />
      </div>

      <div className="kiosk-bar" role="region" aria-label="Your selection">
        <div className="kiosk-bar-inner">
          <div className="kiosk-bar-summary" aria-live="polite">
            {notice ? (
              <p className="notice">{notice}</p>
            ) : selectedSeats.length > 0 ? (
              <>
                <p className="mono kiosk-bar-codes">{selected.join('  ')}</p>
                <p className="muted">
                  {floor.sections.find((s) => s.id === selectedSeats[0].section)?.name}
                  {selectedSeats.every((s) => s.power) && ', charging point'}
                  {selectedSeats.some((s) => s.window) && ', window'}
                </p>
              </>
            ) : (
              <p className="muted">
                {groupSize === 1 ? 'Tap a free seat.' : `Tap a free seat and we will fill ${groupSize} together.`}
              </p>
            )}
          </div>
          <div className="kiosk-bar-actions">
            <button type="button" className="btn btn-ghost" onClick={handleBestSeat}>
              Best seat for me
            </button>
            <button
              type="button"
              className="btn btn-primary"
              disabled={selected.length === 0}
              onClick={handleConfirm}
            >
              {selected.length > 1 ? `Confirm ${selected.length} seats` : 'Confirm seat'}
            </button>
          </div>
        </div>
      </div>
    </KioskShell>
  )
}

function KioskShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="kiosk">
      <header className="kiosk-header">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true" />
          <span className="brand-name">SeatSense</span>
          <span className="brand-place">Nava Nalanda Central Library</span>
        </div>
        <span className="sample-tag">Sample data, provisional layout</span>
      </header>
      <main className="kiosk-main">{children}</main>
    </div>
  )
}
