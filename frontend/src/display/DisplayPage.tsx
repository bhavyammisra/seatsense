import { useEffect, useState } from 'react'
import { fetchAvailability, type Availability } from '../shared/api/layoutApi'
import { useLive } from '../shared/live/live'
import { clockTime, predictionFor, predictionSource, usePredictions } from '../public/predictions'
import './display.css'

// Entrance display (WORKPLAN B4.5): a big read-only board for a TV or projector at the entrance.
// Counts update live over /topic/availability. No login, no names, nothing to tap.

const clockFormat = new Intl.DateTimeFormat('en-IN', { hour: 'numeric', minute: '2-digit', timeZone: 'Asia/Kolkata' })

export default function DisplayPage() {
  const [availability, setAvailability] = useState<Availability | null>(null)
  const [unreachable, setUnreachable] = useState(false)
  const [now, setNow] = useState(() => new Date())
  const predictions = usePredictions()

  useEffect(() => {
    fetchAvailability()
      .then(setAvailability)
      .catch(() => setUnreachable(true))
    const tick = setInterval(() => setNow(new Date()), 15_000)
    return () => clearInterval(tick)
  }, [])
  useLive<Availability>('/topic/availability', (a) => {
    setAvailability(a)
    setUnreachable(false)
  })

  const shown = availability
    ? availability.floors.map((f) => predictionFor(predictions, f.id, f.free)).filter((p) => p !== null)
    : []
  const source = predictionSource(shown)

  return (
    <div className="display">
      <header className="display-header">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true" />
          <span className="brand-name">SeatSense</span>
          <span className="brand-place">Nava Nalanda Central Library</span>
        </div>
        <span className="display-clock">{clockFormat.format(now)}</span>
      </header>

      <main className="display-main">
        {!availability ? (
          <p className="display-wait">{unreachable ? 'Seat information is not available right now.' : 'Loading seats…'}</p>
        ) : (
          <>
            <section className="display-total" aria-live="polite">
              {availability.full ? (
                <h1>The library is full</h1>
              ) : (
                <h1>
                  <span className="display-total-count">{availability.free}</span> seats free
                </h1>
              )}
              <p>Scan your ID card at the gate and the kiosk gives you a seat.</p>
            </section>

            <ul className="display-floors">
              {availability.floors.map((f) => {
                const p = predictionFor(predictions, f.id, f.free)
                return (
                  <li key={f.id} className="display-floor" data-full={f.full || undefined}>
                    <span className="display-floor-name">{f.name}</span>
                    <span className="display-floor-count">{f.full ? 'Full' : f.free}</span>
                    <span className="display-floor-of">{f.full ? `all ${f.total} seats taken` : `free of ${f.total}`}</span>
                    <span className="display-floor-bar" aria-hidden="true">
                      <span style={{ width: `${f.total ? ((f.total - f.free) / f.total) * 100 : 0}%` }} />
                    </span>
                    {p?.freesUpAround && (
                      <span className="display-floor-usual">Usually frees up around {clockTime(p.freesUpAround)}</span>
                    )}
                  </li>
                )
              })}
            </ul>

            {availability.full && (
              <p className="display-note">
                Join the waitlist on the student portal. The first seat that frees up is held for you.
              </p>
            )}
            {source && <p className="display-source">{source}</p>}
          </>
        )}
      </main>
    </div>
  )
}
