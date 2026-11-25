// Login for staff pages (docs/api.md, Auth). The server uses a session cookie and
// checks a CSRF token on every POST: read the XSRF-TOKEN cookie and send it back
// as the X-XSRF-TOKEN header. Any GET sets that cookie, so fetch one first if needed.

export type Role = 'STUDENT' | 'LIBRARIAN' | 'ADMIN' | 'GATE'

export type Me = {
  username: string
  role: Role
  student: { id: number; name: string; initials: string; sample: boolean } | null
}

function csrfToken(): string {
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/)
  return match ? decodeURIComponent(match[1]) : ''
}

export async function ensureCsrf(): Promise<string> {
  if (!csrfToken()) await fetch('/api/public/availability', { credentials: 'same-origin' })
  return csrfToken()
}

/** The logged-in account, or null if nobody is logged in. */
export async function fetchMe(): Promise<Me | null> {
  const response = await fetch('/api/auth/me', { credentials: 'same-origin' })
  if (response.status === 401) return null
  if (!response.ok) throw new Error(`/api/auth/me answered ${response.status}`)
  return (await response.json()) as Me
}

/** Logs in. Returns the account, or null for a wrong username or password. */
export async function login(username: string, password: string): Promise<Me | null> {
  const token = await ensureCsrf()
  const response = await fetch('/api/auth/login', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token },
    body: JSON.stringify({ username, password }),
  })
  if (response.status === 401 || response.status === 400) return null
  if (!response.ok) throw new Error(`/api/auth/login answered ${response.status}`)
  return (await response.json()) as Me
}

export async function logout(): Promise<void> {
  const token = await ensureCsrf()
  await fetch('/api/auth/logout', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { 'X-XSRF-TOKEN': token },
  })
}
