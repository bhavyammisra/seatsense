import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { fetchMe, login, logout, type Me } from '../shared/api/authApi'
import { setOutOfService, simClock, simHistory, simReset, simRush, simulatorAvailable } from '../shared/api/adminApi'
import {
  fetchAvailability,
  fetchFloors,
  fetchFloorWithSeats,
  fetchHeadcount,
  type ApiFloor,
  type Availability,
  type Headcount,
} from '../shared/api/layoutApi'
import type { Floor, Seat, SeatState, SeatStates } from '../shared/layout'
import { reconnectLive, useLive } from '../shared/live/live'
import SeatMap, { SeatLegend } from '../shared/seatmap/SeatMap'
import './dashboard.css'

// Librarian dashboard (WORKPLAN B1.6): who is in the library right now, floor
// by floor, with a live seat map. Library staff only (LIBRARIAN or ADMIN).

type SeatUpdate = { code: string; state: SeatState; at: string }

const STAFF: Me['role'][] = ['LIBRARIAN', 'ADMIN']

export default function DashboardPage() {
  const [me, setMe] = useState<Me | null | undefined>(undefined)

  useEffect(() => {
    fetchMe()
      .then(setMe)
      .catch(() => setMe(null))
  }, [])

  async function handleLogout() {
    await logout()
    await reconnectLive()
    setMe(null)
  }

  return (
    <div className="dash">
      <header className="dash-header">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true" />
          <span className="brand-name">SeatSense</span>
          <span className="brand-place">Librarian dashboard</span>
        </div>
        {me && (
          <div className="dash-user">
            <span>{me.username}</span>
            <button type="button" className="btn btn-ghost dash-logout" onClick={handleLogout}>
              Log out
            </button>
          </div>
        )}
      </header>
      <main className="dash-main">
        {me === undefined ? (
          <p className="dash-muted">Checking your login…</p>
        ) : me === null ? (
          <LoginForm onLoggedIn={setMe} />
        ) : !STAFF.includes(me.role) ? (
          <section className="dash-panel">
            <h1>This account can't open the dashboard</h1>
            <p className="dash-muted">The dashboard is for library staff. Log out and use a librarian account.</p>
          </section>
        ) : (
          <Dashboard />
        )}
      </main>
    </div>
  )
}

function LoginForm({ onLoggedIn }: { onLoggedIn: (me: Me) => void }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!username.trim() || !password) {
      setError('Enter your username and password.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const account = await login(username.trim(), password)
      if (!account) {
        setError('Wrong username or password.')
        return
      }
      await reconnectLive()
      onLoggedIn(account)
    } catch {
      setError('The library system could not be reached. Try again in a minute.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="dash-panel dash-login" onSubmit={submit} noValidate>
      <h1>Log in</h1>
      <p className="dash-muted">For library staff.</p>
      <label>
        Username
        <input
          value={username}
          onChange={(e) => {
            setUsername(e.target.value)
            setError(null)
          }}
          autoComplete="username"
          autoFocus
        />
      </label>
      <label>
        Password
        <input
          type="password"
          value={password}
          onChange={(e) => {
            setPassword(e.target.value)
            setError(null)
          }}
          autoComplete="current-password"
        />
      </label>
      {error && (
        <p className="dash-error" role="alert">
          {error}
        </p>
      )}
      <button type="submit" className="btn btn-primary" disabled={busy}>
        {busy ? 'Logging in…' : 'Log in'}
      </button>
    </form>
  )
}

function Dashboard() {
  const [floors, setFloors] = useState<ApiFloor[] | null>(null)
  const [availability, setAvailability] = useState<Availability | null>(null)
  const [headcount, setHeadcount] = useState<Headcount | null>(null)
  const [floorId, setFloorId] = useState<string | null>(null)
  const [map, setMap] = useState<{ floor: Floor; states: SeatStates } | null>(null)
  const [error, setError] = useState(false)
  const [pending, setPending] = useState<Seat | null>(null)
  const [actionMessage, setActionMessage] = useState<string | null>(null)
  const [demo, setDemo] = useState(false)
  const [demoBusy, setDemoBusy] = useState(false)

  useEffect(() => {
    simulatorAvailable().then(setDemo)
  }, [])

  async function toggleService(seat: Seat, outOfService: boolean) {
    setActionMessage(null)
    try {
      await setOutOfService(seat.code, outOfService)
      setActionMessage(outOfService ? `${seat.code} is out of service.` : `${seat.code} is back in service.`)
    } catch {
      setActionMessage(`${seat.code} could not be changed. Only a free seat can go out of service.`)
    } finally {
      setPending(null)
    }
  }

  async function runDemo(action: () => Promise<unknown>, done: string) {
    setDemoBusy(true)
    setActionMessage(null)
    try {
      await action()
      setActionMessage(done)
    } catch {
      setActionMessage('The demo action did not go through.')
    } finally {
      setDemoBusy(false)
    }
  }

  useEffect(() => {
    let cancelled = false
    Promise.all([fetchFloors(), fetchAvailability(), fetchHeadcount()])
      .then(([f, a, h]) => {
        if (cancelled) return
        setFloors(f)
        setAvailability(a)
        setHeadcount(h)
        setFloorId(f[0]?.id ?? null)
      })
      .catch(() => !cancelled && setError(true))
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    if (!floors || !floorId) return
    const floor = floors.find((f) => f.id === floorId)
    if (!floor) return
    let cancelled = false
    fetchFloorWithSeats(floor)
      .then((m) => !cancelled && setMap(m))
      .catch(() => !cancelled && setError(true))
    return () => {
      cancelled = true
    }
  }, [floors, floorId])

  // Counts arrive live; the headcount has no topic, so refresh it whenever seats change.
  const onAvailability = useCallback((a: Availability) => {
    setAvailability(a)
    fetchHeadcount()
      .then(setHeadcount)
      .catch(() => undefined)
  }, [])
  useLive<Availability>('/topic/availability', onAvailability)

  const onSeatUpdate = useCallback((update: SeatUpdate) => {
    setMap((current) =>
      current ? { ...current, states: { ...current.states, [update.code]: update.state } } : current,
    )
  }, [])
  useLive<SeatUpdate>(floorId ? `/topic/seats/${floorId}` : null, onSeatUpdate)

  if (error) {
    return (
      <section className="dash-panel" role="alert">
        <h1>Dashboard data is not available</h1>
        <p className="dash-muted">The library system could not be reached. Reload the page to try again.</p>
      </section>
    )
  }
  if (!floors || !availability || !headcount) {
    return <p className="dash-muted">Loading…</p>
  }

  const insideOn = (id: string) => headcount.floors.find((f) => f.id === id)?.inside ?? 0
  const takenShare = availability.total ? (availability.total - availability.free) / availability.total : 0

  return (
    <>
      <section className="dash-stats" aria-label="Right now">
        <Stat label="In seats now" value={headcount.inside} />
        <Stat label="On a break" value={headcount.onBreak} />
        <Stat label="Free seats" value={availability.free} tone={availability.full ? 'danger' : 'ok'} />
        <Stat label="Occupancy" value={`${Math.round(takenShare * 100)}%`} hint={`of ${availability.total} seats`} />
      </section>

      <section className="dash-panel">
        <h2>By floor</h2>
        <table className="dash-table">
          <thead>
            <tr>
              <th scope="col">Floor</th>
              <th scope="col">In seats</th>
              <th scope="col">Free</th>
              <th scope="col">Seats</th>
              <th scope="col">Occupancy</th>
            </tr>
          </thead>
          <tbody>
            {availability.floors.map((f) => {
              const share = f.total ? (f.total - f.free) / f.total : 0
              return (
                <tr key={f.id} aria-current={f.id === floorId ? 'true' : undefined}>
                  <th scope="row">
                    <button type="button" className="dash-floor-link" onClick={() => setFloorId(f.id)}>
                      {f.name}
                    </button>
                  </th>
                  <td>{insideOn(f.id)}</td>
                  <td data-full={f.full || undefined}>{f.full ? 'Full' : f.free}</td>
                  <td>{f.total}</td>
                  <td>
                    <span className="dash-bar" aria-hidden="true">
                      <span style={{ width: `${share * 100}%` }} />
                    </span>
                    <span className="dash-share">{Math.round(share * 100)}%</span>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </section>

      {demo && (
        <section className="dash-panel dash-demo" aria-label="Demo controls">
          <h2>Demo controls</h2>
          <p className="dash-muted">
            Only on a dev run. Simulated students fill the library like an exam evening; the clock moves forward so
            breaks and waitlist offers run out on stage.
          </p>
          <div className="dash-demo-buttons">
            <button
              type="button"
              className="btn btn-primary"
              disabled={demoBusy}
              onClick={() => runDemo(simRush, 'Exam evening started: the library is filling up.')}
            >
              Exam evening rush
            </button>
            <button
              type="button"
              className="btn btn-ghost"
              disabled={demoBusy}
              onClick={() => runDemo(() => simClock(30), 'Clock moved 30 minutes forward.')}
            >
              Clock +30 min
            </button>
            <button
              type="button"
              className="btn btn-ghost"
              disabled={demoBusy}
              onClick={() => runDemo(simReset, 'Everyone has left and the waitlist is empty.')}
            >
              Reset
            </button>
            <button
              type="button"
              className="btn btn-ghost"
              disabled={demoBusy}
              onClick={() =>
                runDemo(simHistory, '4 weeks of sample history written. Predictions and the heatmap now use it.')
              }
            >
              Sample history
            </button>
          </div>
        </section>
      )}

      <section className="dash-panel dash-map" aria-label="Seat map">
        <h2>{floors.find((f) => f.id === floorId)?.name ?? 'Seat map'}</h2>
        <p className="dash-muted dash-hint">Click a free seat to take it out of service, or a dotted seat to put it back.</p>
        {pending && (
          <div className="dash-confirm" role="alertdialog" aria-label="Confirm seat change">
            <span>
              {map?.states[pending.code] === 'OUT_OF_SERVICE'
                ? `Put ${pending.code} back into service?`
                : `Take ${pending.code} out of service?`}
            </span>
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => toggleService(pending, map?.states[pending.code] !== 'OUT_OF_SERVICE')}
            >
              Confirm
            </button>
            <button type="button" className="btn btn-ghost" onClick={() => setPending(null)}>
              Cancel
            </button>
          </div>
        )}
        {actionMessage && (
          <p className="dash-action" role="status">
            {actionMessage}
          </p>
        )}
        {map && map.floor.id === floorId ? (
          <>
            <SeatMap
              floor={map.floor}
              states={map.states}
              clickableStates={['FREE', 'OUT_OF_SERVICE']}
              onSeatClick={setPending}
            />
            <SeatLegend showSelected={false} />
          </>
        ) : (
          <p className="dash-muted">Loading the floor…</p>
        )}
      </section>
    </>
  )
}

function Stat({
  label,
  value,
  hint,
  tone,
}: {
  label: string
  value: number | string
  hint?: string
  tone?: 'ok' | 'danger'
}) {
  return (
    <div className="dash-stat" data-tone={tone}>
      <span className="dash-stat-label">{label}</span>
      <span className="dash-stat-value">{value}</span>
      {hint && <span className="dash-stat-hint">{hint}</span>}
    </div>
  )
}
