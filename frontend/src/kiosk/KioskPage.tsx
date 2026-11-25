import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { fetchAvailability, fetchFloors, fetchFloorWithSeats, type ApiFloor, type Availability } from '../shared/api/layoutApi'
import {
  ApiError,
  exitSession,
  takeSeats,
  type Choice,
  type ScanResult,
  type SessionView,
  type StudentView,
} from '../shared/api/gateApi'
import StaffLogin from '../shared/auth/StaffLogin'
import type { Floor, Seat, SeatState, SeatStates } from '../shared/layout'
import { useLive } from '../shared/live/live'
import SeatMap, { SeatLegend } from '../shared/seatmap/SeatMap'
import { pickTogether } from '../shared/seatmap/selection'
import './kiosk.css'

// Gate kiosk (WORKPLAN S1.8, with S2.2 and the S2.3 kiosk flow). The phone scanner
// reports a card; the server decides ENTRY, EXIT or RETURN and sends it here over
// /topic/gate/MAIN. The kiosk then lets the student pick a seat, leave, or take a
// break. Logged in once with the gate account.

const GATE_ID = 'MAIN'
const AUTO_ASSIGN_SECONDS = 10
const BACK_TO_IDLE_MS = 12000
const MAX_BREAKS = 2
const GROUP_SIZES = [1, 2, 3, 4]

type SeatUpdate = { code: string; state: SeatState; at: string }

type Screen =
  | { kind: 'idle' }
  | { kind: 'gather'; leader: StudentView; lastSeat: string | null; size: number; members: StudentView[] }
  | { kind: 'pick'; students: StudentView[]; lastSeat: string | null }
  | { kind: 'exit'; student: StudentView; session: SessionView }
  | { kind: 'done'; title: string; codes: string[]; lines: string[] }

const timeFormat = new Intl.DateTimeFormat('en-IN', { hour: 'numeric', minute: '2-digit', timeZone: 'Asia/Kolkata' })

export default function KioskPage() {
  return (
    <StaffLogin roles={['GATE']} title="Gate kiosk" hint="Log in once with the gate account on this screen.">
      {() => <Kiosk />}
    </StaffLogin>
  )
}

function Kiosk() {
  const [screen, setScreen] = useState<Screen>({ kind: 'idle' })
  const [availability, setAvailability] = useState<Availability | null>(null)
  const [floors, setFloors] = useState<ApiFloor[] | null>(null)
  const [loadError, setLoadError] = useState(false)

  useEffect(() => {
    Promise.all([fetchFloors(), fetchAvailability()])
      .then(([f, a]) => {
        setFloors(f)
        setAvailability(a)
      })
      .catch(() => setLoadError(true))
  }, [])
  useLive<Availability>('/topic/availability', setAvailability)

  // Every scan at the gate lands here.
  const onScan = useCallback((scan: ScanResult) => {
    setScreen((current) => {
      if (current.kind === 'gather' && scan.mode === 'ENTRY') {
        const already = [current.leader, ...current.members].some((s) => s.id === scan.student.id)
        if (already) return current
        const members = [...current.members, scan.student]
        if (members.length + 1 >= current.size) {
          return { kind: 'pick', students: [current.leader, ...members], lastSeat: current.lastSeat }
        }
        return { ...current, members }
      }
      if (scan.mode === 'ENTRY') return { kind: 'pick', students: [scan.student], lastSeat: scan.lastSeat }
      if (scan.mode === 'EXIT' && scan.session) return { kind: 'exit', student: scan.student, session: scan.session }
      return {
        kind: 'done',
        title: `Welcome back, ${scan.student.name}`,
        codes: scan.session ? [scan.session.seat] : [],
        lines: ['Your seat was kept for you.'],
      }
    })
  }, [])
  useLive<ScanResult>(`/topic/gate/${GATE_ID}`, onScan)

  // Finished screens go back to "scan your card" on their own.
  useEffect(() => {
    if (screen.kind !== 'done') return
    const timer = setTimeout(() => setScreen({ kind: 'idle' }), BACK_TO_IDLE_MS)
    return () => clearTimeout(timer)
  }, [screen])

  return (
    <div className="kiosk">
      <header className="kiosk-header">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true" />
          <span className="brand-name">SeatSense</span>
          <span className="brand-place">Nava Nalanda Central Library</span>
        </div>
        {availability && (
          <span className="kiosk-free" data-full={availability.full || undefined}>
            {availability.full ? 'Library full' : `${availability.free} seats free`}
          </span>
        )}
      </header>
      <main className="kiosk-main">
        {loadError ? (
          <section className="kiosk-panel" role="alert">
            <h1>The kiosk cannot reach the library system</h1>
            <p className="muted">Check the network, then reload this page.</p>
          </section>
        ) : screen.kind === 'idle' ? (
          <Idle availability={availability} />
        ) : screen.kind === 'gather' ? (
          <Gather screen={screen} onCancel={() => setScreen({ kind: 'idle' })} />
        ) : screen.kind === 'pick' && floors && availability ? (
          <Picker
            key={screen.students.map((s) => s.id).join('-')}
            students={screen.students}
            lastSeat={screen.lastSeat}
            floors={floors}
            availability={availability}
            onGroup={(size) =>
              setScreen({ kind: 'gather', leader: screen.students[0], lastSeat: screen.lastSeat, size, members: [] })
            }
            onDone={setScreen}
            onCancel={() => setScreen({ kind: 'idle' })}
          />
        ) : screen.kind === 'exit' ? (
          <Exit screen={screen} onDone={setScreen} />
        ) : screen.kind === 'done' ? (
          <Done screen={screen} onClose={() => setScreen({ kind: 'idle' })} />
        ) : (
          <p className="muted">Loading the library…</p>
        )}
      </main>
    </div>
  )
}

function Idle({ availability }: { availability: Availability | null }) {
  return (
    <section className="kiosk-idle">
      <h1>Scan your ID card at the scanner</h1>
      <p className="muted">
        {availability
          ? availability.full
            ? 'Every seat is taken right now. Seats free up through the day.'
            : `${availability.free} of ${availability.total} seats are free.`
          : 'Loading seats…'}
      </p>
      <p className="kiosk-idle-hint">Scan again when you leave, or to take a break.</p>
    </section>
  )
}

function Gather({ screen, onCancel }: { screen: Extract<Screen, { kind: 'gather' }>; onCancel: () => void }) {
  const waiting = screen.size - 1 - screen.members.length
  return (
    <section className="kiosk-panel kiosk-gather">
      <h1>
        Seats for {screen.size}: ask your {waiting === 1 ? 'friend' : `${waiting} friends`} to scan now
      </h1>
      <ul className="kiosk-gather-list">
        {[screen.leader, ...screen.members].map((s) => (
          <li key={s.id}>
            <span className="avatar" aria-hidden="true">
              {s.initials}
            </span>
            {s.name}
          </li>
        ))}
        {Array.from({ length: waiting }, (_, i) => (
          <li key={`wait-${i}`} className="kiosk-gather-waiting">
            Waiting for a scan…
          </li>
        ))}
      </ul>
      <button type="button" className="btn btn-ghost" onClick={onCancel}>
        Cancel
      </button>
    </section>
  )
}

function Picker({
  students,
  lastSeat,
  floors,
  availability,
  onGroup,
  onDone,
  onCancel,
}: {
  students: StudentView[]
  lastSeat: string | null
  floors: ApiFloor[]
  availability: Availability
  onGroup: (size: number) => void
  onDone: (screen: Screen) => void
  onCancel: () => void
}) {
  const leader = students[0]
  const groupSize = students.length
  const [floorId, setFloorId] = useState(
    () => (availability.floors.find((f) => f.free >= groupSize) ?? availability.floors[0]).id,
  )
  const [map, setMap] = useState<{ floor: Floor; states: SeatStates } | null>(null)
  const [selected, setSelected] = useState<string[]>([])
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [countdown, setCountdown] = useState<number | null>(groupSize === 1 ? AUTO_ASSIGN_SECONDS : null)
  const autoFired = useRef(false)

  useEffect(() => {
    const floor = floors.find((f) => f.id === floorId)
    if (!floor) return
    let cancelled = false
    fetchFloorWithSeats(floor)
      .then((m) => !cancelled && setMap(m))
      .catch(() => !cancelled && setNotice('This floor could not be loaded. Pick another floor.'))
    return () => {
      cancelled = true
    }
  }, [floors, floorId])

  const onSeatUpdate = useCallback((update: SeatUpdate) => {
    setMap((current) => (current ? { ...current, states: { ...current.states, [update.code]: update.state } } : current))
    setSelected((current) => (current.includes(update.code) && update.state !== 'FREE' ? [] : current))
  }, [])
  useLive<SeatUpdate>(`/topic/seats/${floorId}`, onSeatUpdate)

  const submit = useCallback(
    async (choice: Choice) => {
      setBusy(true)
      setNotice(null)
      setCountdown(null)
      try {
        const sessions =
          groupSize > 1
            ? await takeSeats(leader.id, {
                type: 'GROUP',
                studentIds: students.map((s) => s.id),
                seats: choice.type === 'GROUP' ? choice.seats : undefined,
              })
            : await takeSeats(leader.id, choice)
        onDone({
          kind: 'done',
          title: groupSize > 1 ? 'Your seats' : 'Your seat',
          codes: sessions.map((s) => s.seat),
          lines: [
            groupSize > 1 ? students.map((s) => s.name).join(', ') : leader.name,
            'Scan your ID at the gate when you leave. Choose Break if you are coming back within 30 minutes.',
          ],
        })
      } catch (e) {
        setSelected([])
        if (e instanceof ApiError && e.error === 'SEAT_TAKEN') {
          const alternative = (e.details.alternative as { code?: string } | null)?.code
          setNotice(alternative ? `That seat was just taken. ${alternative} is free nearby.` : 'Every seat is taken right now.')
        } else if (e instanceof ApiError && e.error === 'GROUP_NOT_AVAILABLE') {
          setNotice(`No ${groupSize} free seats together right now. Try another floor or fewer seats.`)
        } else if (e instanceof ApiError && e.error === 'ALREADY_INSIDE') {
          setNotice('This card already has a seat. Scan it again to leave first.')
        } else {
          setNotice('That did not go through. Try again.')
        }
      } finally {
        setBusy(false)
      }
    },
    [groupSize, leader, students, onDone],
  )

  // S2.2: if nobody touches the screen for 10 seconds, give them the best seat.
  useEffect(() => {
    if (countdown === null) return
    if (countdown <= 0) {
      if (!autoFired.current) {
        autoFired.current = true
        void submit({ type: 'BEST' })
      }
      return
    }
    const timer = setTimeout(() => setCountdown((c) => (c === null ? null : c - 1)), 1000)
    return () => clearTimeout(timer)
  }, [countdown, submit])

  const stopCountdown = () => setCountdown(null)

  const selectedSeats = useMemo(
    () => selected.map((c) => map?.floor.seats.find((s) => s.code === c)).filter((s): s is Seat => Boolean(s)),
    [selected, map],
  )

  function handleSeatClick(seat: Seat) {
    if (!map) return
    stopCountdown()
    if (selected.includes(seat.code)) {
      setSelected([])
      return
    }
    const pick = pickTogether(map.floor, seat, groupSize, map.states)
    if (pick) {
      setSelected(pick)
      setNotice(null)
    } else {
      setNotice(`No ${groupSize} free seats together there. Try another row or a group table.`)
    }
  }

  return (
    <div className="kiosk-pick" onPointerDown={stopCountdown}>
      <div className="kiosk-top">
        <div className="kiosk-welcome">
          <span className="avatar" aria-hidden="true">
            {leader.initials}
          </span>
          <div>
            <h1>
              Welcome, {leader.name}
              {students.length > 1 && ` and ${students.length - 1} more`}
            </h1>
            <p className="muted">
              {countdown !== null
                ? `Picking the best seat for you in ${countdown} s. Tap anywhere to choose yourself.`
                : groupSize > 1
                  ? `Tap a free seat and we will fill ${groupSize} together.`
                  : 'Tap a free seat.'}
            </p>
          </div>
        </div>
        {groupSize === 1 && (
          <fieldset className="group-size">
            <legend>How many seats?</legend>
            <div className="group-size-options">
              {GROUP_SIZES.map((n) => (
                <button
                  key={n}
                  type="button"
                  className="group-size-option"
                  aria-pressed={n === 1}
                  onClick={() => n > 1 && onGroup(n)}
                >
                  {n}
                </button>
              ))}
            </div>
          </fieldset>
        )}
      </div>

      <nav className="floor-tabs" aria-label="Floors">
        {availability.floors.map((f) => (
          <button
            key={f.id}
            type="button"
            className="floor-tab"
            aria-current={f.id === floorId ? 'true' : undefined}
            data-full={f.full || undefined}
            onClick={() => {
              setFloorId(f.id)
              setSelected([])
              setNotice(null)
            }}
          >
            <span className="floor-tab-name">{f.name}</span>
            <span className="floor-tab-free">{f.full ? 'Full' : `${f.free} free`}</span>
          </button>
        ))}
      </nav>

      <div className="kiosk-map card">
        {map && map.floor.id === floorId ? (
          <>
            <SeatMap floor={map.floor} states={map.states} selected={selected} onSeatClick={handleSeatClick} />
            <SeatLegend />
          </>
        ) : (
          <p className="muted">Loading the floor…</p>
        )}
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
                  {map?.floor.sections.find((s) => s.id === selectedSeats[0].section)?.name}
                  {selectedSeats.every((s) => s.power) && ', charging point'}
                  {selectedSeats.some((s) => s.window) && ', window'}
                </p>
              </>
            ) : (
              <p className="muted">{groupSize > 1 ? `${groupSize} seats together` : 'No seat chosen yet'}</p>
            )}
          </div>
          <div className="kiosk-bar-actions">
            <button type="button" className="btn btn-ghost" onClick={onCancel} disabled={busy}>
              Cancel
            </button>
            {groupSize === 1 && lastSeat && (
              <button
                type="button"
                className="btn btn-ghost"
                disabled={busy}
                onClick={() => submit({ type: 'SAME_AS_LAST' })}
              >
                Same as last time ({lastSeat})
              </button>
            )}
            <button
              type="button"
              className="btn btn-ghost"
              disabled={busy}
              onClick={() => submit(groupSize > 1 ? { type: 'GROUP', studentIds: [] } : { type: 'BEST' })}
            >
              {groupSize > 1 ? 'Find seats for us' : 'Best seat for me'}
            </button>
            <button
              type="button"
              className="btn btn-primary"
              disabled={busy || selected.length !== groupSize}
              onClick={() =>
                submit(
                  groupSize > 1
                    ? { type: 'GROUP', studentIds: [], seats: selected }
                    : { type: 'SEAT', seat: selected[0] },
                )
              }
            >
              {busy ? 'Saving…' : groupSize > 1 ? `Confirm ${groupSize} seats` : 'Confirm seat'}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

function Exit({ screen, onDone }: { screen: Extract<Screen, { kind: 'exit' }>; onDone: (s: Screen) => void }) {
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)
  const breaksLeft = Math.max(0, MAX_BREAKS - screen.session.breaksUsedToday)

  async function choose(type: 'LEAVE' | 'BREAK') {
    setBusy(true)
    setNotice(null)
    try {
      const session = await exitSession(screen.session.id, type)
      onDone(
        type === 'BREAK'
          ? {
              kind: 'done',
              title: 'Enjoy your break',
              codes: [session.seat],
              lines: [
                session.breakUntil
                  ? `Your seat is kept until ${timeFormat.format(new Date(session.breakUntil))}. Scan your ID when you are back.`
                  : 'Scan your ID when you are back.',
              ],
            }
          : {
              kind: 'done',
              title: `Goodbye, ${screen.student.name}`,
              codes: [],
              lines: [`Seat ${session.seat} is free again.`],
            },
      )
    } catch (e) {
      setNotice(
        e instanceof ApiError && e.error === 'NO_BREAKS_LEFT'
          ? "All of today's breaks are used. Choose Leaving instead."
          : 'That did not go through. Try again.',
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="kiosk-panel kiosk-exit">
      <div className="kiosk-welcome">
        <span className="avatar" aria-hidden="true">
          {screen.student.initials}
        </span>
        <div>
          <h1>Leaving, {screen.student.name}?</h1>
          <p className="muted">
            Seat <span className="mono">{screen.session.seat}</span>
          </p>
        </div>
      </div>
      <div className="kiosk-exit-choices">
        <button
          type="button"
          className="btn btn-primary kiosk-exit-choice"
          disabled={busy}
          onClick={() => choose('LEAVE')}
        >
          Leaving
          <span>Your seat is freed for someone else</span>
        </button>
        <button
          type="button"
          className="btn btn-ghost kiosk-exit-choice"
          disabled={busy || breaksLeft === 0}
          onClick={() => choose('BREAK')}
        >
          Break, 30 minutes
          <span>{breaksLeft === 0 ? 'No breaks left today' : `${breaksLeft} left today, seat kept for you`}</span>
        </button>
      </div>
      {notice && (
        <p className="notice" role="alert">
          {notice}
        </p>
      )}
    </section>
  )
}

function Done({ screen, onClose }: { screen: Extract<Screen, { kind: 'done' }>; onClose: () => void }) {
  return (
    <div className="kiosk-done">
      <div className="ticket">
        <p className="ticket-label">{screen.title}</p>
        {screen.codes.length > 0 && <p className="ticket-code mono">{screen.codes.join('  ')}</p>}
        {screen.lines.map((line) => (
          <p key={line} className="ticket-foot">
            {line}
          </p>
        ))}
      </div>
      <button type="button" className="btn btn-primary" onClick={onClose}>
        Done
      </button>
    </div>
  )
}
