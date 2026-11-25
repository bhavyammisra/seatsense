import { useEffect, useRef, useState, type FormEvent } from 'react'
import { ApiError, scanCard, type ScanResult } from '../shared/api/gateApi'
import StaffLogin from '../shared/auth/StaffLogin'
import './scanner.css'

// Phone ID scanner at the library gate (WORKPLAN S1.7). Reads the ID card's
// barcode with the camera and reports it to the server, which decides whether
// it is an entry, exit or return and tells the kiosk screen.

const GATE_ID = 'MAIN'
const REPEAT_IGNORE_MS = 4000

type Outcome =
  | { kind: 'ok'; result: ScanResult }
  | { kind: 'error'; message: string }

const MODE_TEXT: Record<ScanResult['mode'], string> = {
  ENTRY: 'Entry. Choose a seat on the kiosk screen.',
  EXIT: 'Exit. Choose Leaving or Break on the kiosk screen.',
  RETURN: 'Back from break. Same seat as before.',
}

export default function ScannerPage() {
  return (
    <StaffLogin roles={['GATE']} title="ID scanner" hint="Log in once with the gate account on this phone.">
      {(me, signOut) => <Scanner username={me.username} onSignOut={signOut} />}
    </StaffLogin>
  )
}

function Scanner({ username, onSignOut }: { username: string; onSignOut: () => Promise<void> }) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const stopRef = useRef<(() => void) | null>(null)
  const lastRef = useRef<{ text: string; at: number } | null>(null)
  const [camera, setCamera] = useState<'off' | 'starting' | 'on' | 'failed'>('off')
  const [busy, setBusy] = useState(false)
  const [outcome, setOutcome] = useState<Outcome | null>(null)
  const [manual, setManual] = useState('')
  const [manualError, setManualError] = useState<string | null>(null)

  useEffect(() => () => stopRef.current?.(), [])

  async function report(cardId: string) {
    const text = cardId.trim()
    if (!text) return
    const now = Date.now()
    if (lastRef.current && lastRef.current.text === text && now - lastRef.current.at < REPEAT_IGNORE_MS) return
    lastRef.current = { text, at: now }
    setBusy(true)
    try {
      setOutcome({ kind: 'ok', result: await scanCard(text, GATE_ID) })
      navigator.vibrate?.(80)
    } catch (e) {
      const message =
        e instanceof ApiError && e.error === 'UNKNOWN_CARD'
          ? 'This card is not registered with the library.'
          : e instanceof ApiError && e.status === 0
            ? 'The library system could not be reached.'
            : 'The scan did not go through. Try again.'
      setOutcome({ kind: 'error', message })
    } finally {
      setBusy(false)
    }
  }

  async function startCamera() {
    if (!videoRef.current) return
    setCamera('starting')
    try {
      // Loaded only when the camera starts, so the page stays light.
      const { BrowserMultiFormatReader } = await import('@zxing/browser')
      const reader = new BrowserMultiFormatReader()
      const controls = await reader.decodeFromConstraints(
        { video: { facingMode: 'environment' } },
        videoRef.current,
        (result) => {
          if (result) void report(result.getText())
        },
      )
      stopRef.current = () => controls.stop()
      setCamera('on')
    } catch {
      setCamera('failed')
    }
  }

  function stopCamera() {
    stopRef.current?.()
    stopRef.current = null
    setCamera('off')
  }

  function submitManual(event: FormEvent) {
    event.preventDefault()
    if (!manual.trim()) {
      setManualError('Type the number printed under the barcode.')
      return
    }
    lastRef.current = null // typed on purpose: never ignore it as a repeat
    void report(manual)
    setManual('')
  }

  return (
    <div className="scanner">
      <header className="scanner-header">
        <div>
          <p className="scanner-brand">SeatSense scanner</p>
          <p className="scanner-gate">Main gate · {username}</p>
        </div>
        <button type="button" className="btn btn-ghost scanner-logout" onClick={onSignOut}>
          Log out
        </button>
      </header>

      <main className="scanner-main">
        <section className="scanner-camera" data-state={camera}>
          <video ref={videoRef} muted playsInline aria-label="Camera view" />
          {camera !== 'on' && (
            <div className="scanner-camera-cover">
              {camera === 'failed' ? (
                <p>
                  The camera could not start. Phones only allow the camera on a secure (https) address. Type the
                  card number below instead.
                </p>
              ) : (
                <button
                  type="button"
                  className="btn btn-primary"
                  onClick={startCamera}
                  disabled={camera === 'starting'}
                >
                  {camera === 'starting' ? 'Starting camera…' : 'Start camera'}
                </button>
              )}
            </div>
          )}
          {camera === 'on' && <span className="scanner-frame" aria-hidden="true" />}
        </section>
        {camera === 'on' && (
          <button type="button" className="btn btn-ghost" onClick={stopCamera}>
            Stop camera
          </button>
        )}

        <section className="scanner-outcome" aria-live="polite">
          {busy ? (
            <p className="scanner-muted">Checking the card…</p>
          ) : outcome?.kind === 'ok' ? (
            <div className="scanner-result" data-mode={outcome.result.mode}>
              <span className="scanner-avatar" aria-hidden="true">
                {outcome.result.student.initials}
              </span>
              <div>
                <p className="scanner-name">
                  {outcome.result.student.name}
                  {outcome.result.student.sample && <span className="scanner-sample">Sample</span>}
                </p>
                <p>{MODE_TEXT[outcome.result.mode]}</p>
              </div>
            </div>
          ) : outcome?.kind === 'error' ? (
            <p className="scanner-error" role="alert">
              {outcome.message}
            </p>
          ) : (
            <p className="scanner-muted">Hold the ID card's barcode inside the frame.</p>
          )}
        </section>

        <form className="scanner-manual" onSubmit={submitManual} noValidate>
          <label htmlFor="card-number">Card number</label>
          <div className="scanner-manual-row">
            <input
              id="card-number"
              value={manual}
              onChange={(e) => {
                setManual(e.target.value)
                setManualError(null)
              }}
              autoCapitalize="characters"
              autoComplete="off"
              inputMode="text"
            />
            <button type="submit" className="btn btn-primary" disabled={busy}>
              Scan
            </button>
          </div>
          {manualError && <p className="scanner-error">{manualError}</p>}
        </form>
      </main>
    </div>
  )
}
