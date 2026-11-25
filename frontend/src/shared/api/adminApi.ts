import { ensureCsrf } from './authApi'
import { ApiError } from './gateApi'

// Staff-only calls: out-of-service toggle (B3.3) and the dev-only demo simulator (B3.1).

async function call<T>(method: string, url: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (method !== 'GET') {
    headers['X-XSRF-TOKEN'] = await ensureCsrf()
    headers['Content-Type'] = 'application/json'
  }
  const response = await fetch(url, {
    method,
    credentials: 'same-origin',
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await response.text()
  const data = text ? JSON.parse(text) : null
  if (!response.ok) {
    throw new ApiError(response.status, data?.error ?? 'ERROR', data?.message ?? 'Request failed', data?.details ?? {})
  }
  return data as T
}

export function setOutOfService(code: string, outOfService: boolean): Promise<unknown> {
  return call('POST', `/api/admin/seats/${encodeURIComponent(code)}/out-of-service`, { outOfService })
}

/** True when the backend runs with the dev profile, so the demo controls exist. */
export async function simulatorAvailable(): Promise<boolean> {
  try {
    await call('GET', '/api/sim/status')
    return true
  } catch {
    return false
  }
}

export function simRush(): Promise<{ seated: number; skipped: number }> {
  return call('POST', '/api/sim/rush', { scenario: 'EXAM_EVENING' })
}

export function simClock(advanceMinutes: number): Promise<unknown> {
  return call('POST', '/api/sim/clock', { advanceMinutes })
}

export function simReset(): Promise<{ sessionsEnded: number }> {
  return call('POST', '/api/sim/reset')
}

/** Replaces the SAMPLE occupancy history with 4 invented weeks, for predictions and the heatmap (B4.1). */
export function simHistory(): Promise<{ snapshots: number; sample: boolean }> {
  return call('POST', '/api/sim/history')
}

/** Librarian analytics (B4.3). occupancy is 0 to 1. weekday: 1 = Monday. */
export type Analytics = {
  heatmap: { weekday: number; hour: number; occupancy: number }[]
  daysOfData: number
  sample: boolean
  topSeats: { code: string; sessions: number; minutes: number }[]
  seatsSample: boolean
  sawFull: { date: string; count: number }[]
  sawFullTotal: number
}

export function fetchAnalytics(): Promise<Analytics> {
  return call('GET', '/api/admin/analytics')
}

/** Fire-drill list (B4.4): everyone inside or on a break, by floor. Has names: staff only. */
export type DrillPerson = {
  name: string
  cardId: string
  sample: boolean
  seat: string
  since: string
  breakUntil: string | null
}
export type FireDrill = {
  at: string
  inside: number
  onBreak: number
  floors: { id: string; name: string; inside: DrillPerson[]; onBreak: DrillPerson[] }[]
}

export function fetchFireDrill(): Promise<FireDrill> {
  return call('GET', '/api/admin/fire-drill')
}
