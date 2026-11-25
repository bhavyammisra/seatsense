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
import './public.css'
import { LegalLinks } from '../legal/LegalPages'

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

  return (
    <div className="public">
      <header className="public-header">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true" />
          <span className="brand-name">SeatSense</span>
          <span className="brand-place">Nava Nalanda Central Library</span>
        </div>
        <span className="provisional-tag">Provisional layout</span>
      </header>

      <main className="public-main">
        {error ? (
          <section className="public-error" role="alert">
            <h1>Seat information is not available right now</h1>
            <p>The library system could not be reached. Try again in a minute.</p>
          </section>
        ) : !availability || !floors ? (
          <p className="public-loading" aria-live="polite">
            Loading seats…
          </p>
        ) : (
          <>
            <section className="public-summary" aria-live="polite">
              {availability.full ? (
                <>
                  <h1>The library is full</h1>
                  <p>All {availability.total} seats are taken. Seats free up through the day; this page updates on its own.</p>
                  <p>
                    <a className="btn btn-primary public-join" href="/portal">
                      Join the waitlist
                    </a>
                  </p>
                  <p className="public-note">The first seat that frees up is held for you for 15 minutes.</p>
                </>
              ) : (
                <>
                  <h1>
                    <span className="public-count">{availability.free}</span> seats free
                  </h1>
                  <p>
                    of {availability.total} in the library, updated {timeFormat.format(new Date(availability.updatedAt))}
                  </p>
                </>
              )}
              <p className="public-note">
                This page is view only. Seats are given out when you scan your ID card at the entrance.
              </p>
            </section>

            <nav className="public-floors" aria-label="Floors">
              {availability.floors.map((f) => (
                <button
                  key={f.id}
                  type="button"
                  className="public-floor"
                  aria-current={f.id === floorId ? 'true' : undefined}
                  data-full={f.full || undefined}
                  onClick={() => setFloorId(f.id)}
                >
                  <span className="public-floor-name">{f.name}</span>
                  <span className="public-floor-count">
                    {f.full ? 'Full' : `${f.free} free`}
                    <span className="public-floor-total"> of {f.total}</span>
                  </span>
                  <span className="public-floor-bar" aria-hidden="true">
                    <span style={{ width: `${f.total ? ((f.total - f.free) / f.total) * 100 : 0}%` }} />
                  </span>
                </button>
              ))}
            </nav>

            <section className="public-map card" aria-label="Seat map">
              {map && map.floor.id === floorId ? (
                <>
                  <SeatMap floor={map.floor} states={map.states} readOnly />
                  <SeatLegend showSelected={false} />
                </>
              ) : (
                <p className="public-loading">Loading the floor…</p>
              )}
            </section>
          </>
        )}
      </main>
      <LegalLinks />
    </div>
  )
}
