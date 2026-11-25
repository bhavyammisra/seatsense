import { useCallback, useEffect, useState } from 'react'
import { fetchAvailability, type Availability } from '../shared/api/layoutApi'
import { ApiError } from '../shared/api/gateApi'
import { acceptOffer, fetchPlace, joinWaitlist, leaveWaitlist, type Place } from '../shared/api/waitlistApi'
import type { Me } from '../shared/api/authApi'
import StaffLogin from '../shared/auth/StaffLogin'
import { useLive } from '../shared/live/live'
import './portal.css'

// Student portal (WORKPLAN B2.3): join the waitlist when the library is full, see
// your place, and accept a held seat. Offers arrive live on /user/queue/notifications.

type Notification = { type: string; seat?: string; expiresAt?: string }

const timeFormat = new Intl.DateTimeFormat('en-IN', { hour: 'numeric', minute: '2-digit', timeZone: 'Asia/Kolkata' })

export default function PortalPage() {
  return (
    <StaffLogin roles={['STUDENT']} title="Student portal" hint="Log in with your student account.">
      {(me, signOut) => <Portal me={me} onSignOut={signOut} />}
    </StaffLogin>
  )
}

function Portal({ me, onSignOut }: { me: Me; onSignOut: () => Promise<void> }) {
  const [place, setPlace] = useState<Place | null>(null)
  const [availability, setAvailability] = useState<Availability | null>(null)
  const [floorPref, setFloorPref] = useState('')
  const [busy, setBusy] = useState(false)
  const [banner, setBanner] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(() => {
    fetchPlace()
      .then(setPlace)
      .catch(() => setError('Your place could not be loaded. Reload the page.'))
  }, [])

  useEffect(() => {
    refresh()
    fetchAvailability()
      .then(setAvailability)
      .catch(() => undefined)
  }, [refresh])
  useLive<Availability>('/topic/availability', setAvailability)

  const onNotification = useCallback(
    (n: Notification) => {
      if (n.type === 'SEAT_OFFERED') setBanner(`Seat ${n.seat} is held for you. Accept it below.`)
      if (n.type === 'OFFER_EXPIRED') setBanner(`Your offer for seat ${n.seat} ran out.`)
      refresh()
    },
    [refresh],
  )
  useLive<Notification>('/user/queue/notifications', onNotification)

  async function act(action: () => Promise<unknown>) {
    setBusy(true)
    setError(null)
    try {
      await action()
      refresh()
    } catch (e) {
      setError(
        e instanceof ApiError && e.error === 'ALREADY_INSIDE'
          ? 'You already have a seat in the library.'
          : e instanceof ApiError && e.error === 'NO_OFFER'
            ? 'That offer is no longer open.'
            : 'That did not go through. Try again.',
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="portal">
      <header className="portal-header">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true" />
          <span className="brand-name">SeatSense</span>
        </div>
        <div className="portal-user">
          <span>{me.student?.name ?? me.username}</span>
          <button type="button" className="btn btn-ghost portal-logout" onClick={onSignOut}>
            Log out
          </button>
        </div>
      </header>

      <main className="portal-main">
        {availability && (
          <p className="portal-availability" data-full={availability.full || undefined}>
            {availability.full
              ? 'The library is full right now.'
              : `${availability.free} seats are free right now. You can go straight to the gate.`}
          </p>
        )}
        {banner && (
          <p className="portal-banner" role="status">
            {banner}
          </p>
        )}
        {error && (
          <p className="portal-error" role="alert">
            {error}
          </p>
        )}

        {!place ? (
          <p className="portal-muted">Loading…</p>
        ) : place.status === 'WAITING' ? (
          <section className="portal-card">
            <p className="portal-label">Waitlist</p>
            <h1>
              You are <span className="portal-big">#{place.position}</span> in the queue
            </h1>
            <p className="portal-muted">When a seat frees up, it is held for you for 15 minutes and you are told here.</p>
            <button type="button" className="btn btn-ghost" disabled={busy} onClick={() => act(leaveWaitlist)}>
              Leave the queue
            </button>
          </section>
        ) : place.status === 'OFFERED' || place.status === 'ACCEPTED' ? (
          <section className="portal-card portal-offer">
            <p className="portal-label">{place.status === 'OFFERED' ? 'A seat is held for you' : 'Your seat is waiting'}</p>
            <h1 className="mono portal-seat">{place.offeredSeat}</h1>
            {place.offerExpiresAt && <Countdown until={place.offerExpiresAt} onEnd={refresh} />}
            <p className="portal-muted">
              {place.status === 'OFFERED'
                ? 'Accept to get a fresh 15 minutes to walk over. Then scan your ID at the gate.'
                : 'Scan your ID at the gate and the kiosk gives you this seat.'}
            </p>
            <div className="portal-actions">
              {place.status === 'OFFERED' && (
                <button type="button" className="btn btn-primary" disabled={busy} onClick={() => act(acceptOffer)}>
                  Accept, I am coming
                </button>
              )}
              <button type="button" className="btn btn-ghost" disabled={busy} onClick={() => act(leaveWaitlist)}>
                Give it up
              </button>
            </div>
          </section>
        ) : (
          <section className="portal-card">
            <p className="portal-label">Waitlist</p>
            <h1>Not on the waitlist</h1>
            <p className="portal-muted">
              Join when the library is full. The first seat that frees on your floor is held for you for 15 minutes.
            </p>
            <label className="portal-field">
              Floor
              <select value={floorPref} onChange={(e) => setFloorPref(e.target.value)}>
                <option value="">Any floor</option>
                {availability?.floors.map((f) => (
                  <option key={f.id} value={f.id}>
                    {f.name}
                  </option>
                ))}
              </select>
            </label>
            <button
              type="button"
              className="btn btn-primary"
              disabled={busy}
              onClick={() => act(() => joinWaitlist(floorPref || null))}
            >
              Join the waitlist
            </button>
          </section>
        )}
      </main>
    </div>
  )
}

function Countdown({ until, onEnd }: { until: string; onEnd: () => void }) {
  const [now, setNow] = useState(() => Date.now())
  const end = new Date(until).getTime()
  const left = Math.max(0, Math.round((end - now) / 1000))

  useEffect(() => {
    if (left === 0) {
      onEnd()
      return
    }
    const timer = setTimeout(() => setNow(Date.now()), 1000)
    return () => clearTimeout(timer)
  }, [now, left, onEnd])

  const minutes = Math.floor(left / 60)
  const seconds = String(left % 60).padStart(2, '0')
  return (
    <p className="portal-countdown" aria-live="off">
      <span className="mono">
        {minutes}:{seconds}
      </span>{' '}
      left, until {timeFormat.format(new Date(until))}
    </p>
  )
}
