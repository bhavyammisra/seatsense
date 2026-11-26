import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Logo } from '../brand/Logo'
import '../brand/brand.css'
import { fetchMe, login, logout, type Me, type Role } from '../api/authApi'
import { reconnectLive } from '../live/live'
import './staff-login.css'

type Props = {
  /** Roles allowed past the form, for example ['GATE'] for the kiosk and scanner. */
  roles: Role[]
  title: string
  hint: string
  children: (me: Me, signOut: () => Promise<void>) => ReactNode
}

// Login gate for staff screens (kiosk, scanner). The device logs in once with the
// gate account and stays logged in; the page underneath only renders after that.
export default function StaffLogin({ roles, title, hint, children }: Props) {
  const [me, setMe] = useState<Me | null | undefined>(undefined)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    fetchMe()
      .then(setMe)
      .catch(() => setMe(null))
  }, [])

  async function signOut() {
    await logout()
    await reconnectLive()
    setMe(null)
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!username.trim() || !password) {
      setError('Enter the username and password.')
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
      setMe(account)
    } catch {
      setError('The library system could not be reached. Try again in a minute.')
    } finally {
      setBusy(false)
    }
  }

  if (me === undefined) {
    return <p className="staff-login-wait">Checking the login…</p>
  }
  if (me && roles.includes(me.role)) {
    return <>{children(me, signOut)}</>
  }
  if (me) {
    return (
      <LoginFrame title={title}>
        <section className="staff-login-panel">
          <p className="eyebrow">Wrong account</p>
          <p className="staff-login-muted">This screen needs the {roles.join(' or ').toLowerCase()} account.</p>
          <button type="button" className="btn btn-ghost" onClick={signOut}>
            Log out
          </button>
        </section>
      </LoginFrame>
    )
  }
  return (
    <LoginFrame title={title}>
      <form className="staff-login-panel" onSubmit={submit} noValidate>
        <p className="eyebrow">Log in</p>
        <p className="staff-login-muted">{hint}</p>
        <label>
          Username
          <input
            value={username}
            onChange={(e) => {
              setUsername(e.target.value)
              setError(null)
            }}
            autoComplete="username"
            autoCapitalize="none"
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
          <p className="staff-login-error" role="alert">
            {error}
          </p>
        )}
        <button type="submit" className="btn btn-primary" disabled={busy}>
          {busy ? 'Logging in…' : 'Log in'}
        </button>
      </form>
    </LoginFrame>
  )
}

/** Split screen: the page's name large on the wine band, the form on cream. */
export function LoginFrame({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="staff-login">
      <aside className="staff-login-band">
        <Link to="/" className="staff-login-wordmark" aria-label="SeatSense home">
          <Logo />
        </Link>
        <h1>{title}.</h1>
        <p className="staff-login-band-foot">
          <img src="/thapar-logo.jpg" alt="Thapar Institute of Engineering and Technology" width="44" height="39" />
          <span>A course project at Thapar Institute. Sample accounts only.</span>
        </p>
      </aside>
      <main className="staff-login-side">{children}</main>
    </div>
  )
}
