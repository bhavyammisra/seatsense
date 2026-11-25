import { useCallback, useEffect, useState } from 'react'
import {
  ApiUnavailable,
  fetchAvailability,
  fetchFloors,
  fetchFloorWithSeats,
  type ApiFloor,
  type Availability,
} from '../shared/api/layoutApi'
import type { Floor, SeatState, SeatStates } from '../shared/layout'
import { useLive } from '../shared/live/live'
import SeatMap, { SeatLegend } from '../shared/seatmap/SeatMap'
import { LegalFooter } from '../legal/LegalPage'
import './public.css'

// Public availability site (WORKPLAN B1.4). View only: seats are given out at
// the entrance gate, so nothing here can be booked. Counts and the seat map
// update live over /topic/availability and /topic/seats/{floorId}.

type SeatUpdate = { code: string; state: SeatState; at: string }

const timeFormat = new Intl.DateTimeFormat('en-IN', {
  hour: 'numeric',
  minute: '2-digit',
  timeZone: 'Asia/Kolkata',
})

export default function PublicPage() {
  const [floors, setFloors] = useState<ApiFloor[] | null>(null)
  const [availability, setAvailability] = useState<Availability | null>(null)
  const [floorId, setFloorId] = useState<string | null>(null)
  const [map, setMap] = useState<{ floor: Floor; states: SeatStates } | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    Promise.all([fetchFloors(), fetchAvailability()])
      .then(([f, a]) => {
        if (cancelled) return
        setFloors(f)
        setAvailability(a)
        // Open the first floor with free seats, so the map shows somewhere you can sit.
        setFloorId((a.floors.find((x) => x.free > 0) ?? a.floors[0])?.id ?? null)
      })
      .catch((e) => !cancelled && setError(e instanceof ApiUnavailable ? 'unreachable' : 'unknown'))
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
      .catch(() => !cancelled && setError('unreachable'))
    return () => {
      cancelled = true
    }
  }, [floors, floorId])

  useLive<Availability>('/topic/availability', setAvailability)

  const onSeatUpdate = useCallback((update: SeatUpdate) => {
    setMap((current) =>
      current ? { ...current, states: { ...current.states, [update.code]: update.state } } : current,
    )
  }, [])
  useLive<SeatUpdate>(floorId ? `/topic/seats/${floorId}` : null, onSeatUpdate)

  const pickFloor = (id: string) => {
    setFloorId(id)
    document.getElementById('floor-plan')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  const current = availability?.floors.find((f) => f.id === floorId)

  return (
    <div className="public">
      <section className="hero">
        <header className="site-nav">
          <a className="wordmark" href="/">
            SeatSense
            <span>Nava Nalanda Library</span>
          </a>
          <nav aria-label="Main">
            <a href="#floor-plan">Floor plan</a>
            <a href="#how">How it works</a>
            <a href="/portal">Waitlist</a>
            <a href="/dashboard" className="site-nav-staff">
              Staff
            </a>
          </nav>
        </header>

        <div className="hero-body">
          <p className="hero-rail" aria-hidden="true">
            Section 01
          </p>

          <div className="hero-copy" aria-live="polite">
            <p className="eyebrow">Live seats, Nava Nalanda Central Library</p>
            {error ? (
              <>
                <h1 className="hero-title hero-title-small">Seat information is resting.</h1>
                <p className="hero-sub">The library system could not be reached. Try again in a minute.</p>
              </>
            ) : !availability ? (
              <h1 className="hero-title hero-title-small">Counting seats…</h1>
            ) : availability.full ? (
              <>
                <h1 className="hero-title">
                  The library
                  <br />
                  is full.
                </h1>
                <p className="hero-sub">
                  All {availability.total} seats are taken. Join the waitlist and the first seat that frees up is
                  held for you for 15 minutes.
                </p>
                <div className="hero-actions">
                  <a className="btn btn-cream" href="/portal">
                    Join the waitlist
                  </a>
                  <a className="btn btn-light" href="#floor-plan">
                    See the floor plan
                  </a>
                </div>
              </>
            ) : (
              <>
                <h1 className="hero-title">
                  <span className="hero-count">{availability.free}</span>
                  <br />
                  seats free.
                </h1>
                <p className="hero-sub">
                  of {availability.total} across four floors, updated{' '}
                  {timeFormat.format(new Date(availability.updatedAt))}
                </p>
                <div className="hero-actions">
                  <a className="btn btn-cream" href="#floor-plan">
                    See the floor plan
                  </a>
                  <a className="btn btn-light" href="#how">
                    How it works
                  </a>
                </div>
              </>
            )}
          </div>

          {availability && (
            <ol className="hero-floors" aria-label="Floors">
              {availability.floors.map((f, i) => (
                <li key={f.id}>
                  <button
                    type="button"
                    className="hero-floor"
                    aria-current={f.id === floorId ? 'true' : undefined}
                    data-full={f.full || undefined}
                    onClick={() => pickFloor(f.id)}
                  >
                    <span className="hero-floor-num">{String(i + 1).padStart(2, '0')}</span>
                    <span className="hero-floor-name">{f.name}</span>
                    <span className="hero-floor-count">
                      {f.full ? 'Full' : f.free}
                      <small>{f.full ? ` ${f.total} seats` : ` of ${f.total}`}</small>
                    </span>
                    <span className="hero-floor-bar" aria-hidden="true">
                      <span style={{ width: `${f.total ? ((f.total - f.free) / f.total) * 100 : 0}%` }} />
                    </span>
                  </button>
                </li>
              ))}
            </ol>
          )}
        </div>

        <p className="hero-foot">View only. Seats are given out when you scan your ID card at the entrance.</p>
      </section>

      <section id="floor-plan" className="plan">
        <div className="section-head">
          <p className="eyebrow">02 / The floor plan</p>
          <h2>{current ? `${current.name}.` : 'The floor plan.'}</h2>
          {current && (
            <p className="section-lede">
              {current.full ? 'Every seat on this floor is taken.' : `${current.free} of ${current.total} seats free right now.`}{' '}
              The map updates on its own.
            </p>
          )}
        </div>

        {availability && (
          <nav className="plan-tabs" aria-label="Choose a floor">
            {availability.floors.map((f) => (
              <button
                key={f.id}
                type="button"
                aria-current={f.id === floorId ? 'true' : undefined}
                onClick={() => setFloorId(f.id)}
              >
                {f.name}
              </button>
            ))}
          </nav>
        )}

        <div className="plan-map" aria-label="Seat map">
          {map && map.floor.id === floorId ? (
            <>
              <SeatMap floor={map.floor} states={map.states} readOnly />
              <SeatLegend showSelected={false} />
            </>
          ) : (
            <p className="public-loading">{error ? 'The floor plan is not available right now.' : 'Loading the floor…'}</p>
          )}
        </div>
      </section>

      <section id="how" className="how">
        <div className="section-head">
          <p className="eyebrow">03 / How it works</p>
          <h2>From the gate to your seat.</h2>
        </div>
        <ol className="how-steps">
          <li>
            <span className="how-num">i.</span>
            <h3>Scan in</h3>
            <p>Scan your ID card at the entrance gate. The kiosk opens the floor plan for you.</p>
          </li>
          <li>
            <span className="how-num">ii.</span>
            <h3>Pick a seat, or let it pick</h3>
            <p>Tap any free seat. Or wait ten seconds and the kiosk picks the best free seat for you.</p>
          </li>
          <li>
            <span className="how-num">iii.</span>
            <h3>Take a break, or leave</h3>
            <p>Scan out to leave. Going for chai? Choose Break and your seat waits up to 30 minutes, twice a day.</p>
          </li>
          <li>
            <span className="how-num">iv.</span>
            <h3>Full? Wait in line</h3>
            <p>When every seat is taken, join the waitlist. The first seat that frees up is held for you for 15 minutes.</p>
          </li>
        </ol>
      </section>

      <LegalFooter />
    </div>
  )
}
