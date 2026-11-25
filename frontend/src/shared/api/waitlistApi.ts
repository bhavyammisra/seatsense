import { ensureCsrf } from './authApi'
import { ApiError } from './gateApi'

// Client for the student waitlist (docs/api.md, "Student portal"). STUDENT login only.

export type Place = {
  status: 'NONE' | 'WAITING' | 'OFFERED' | 'ACCEPTED' | 'EXPIRED' | 'CANCELLED' | 'DONE'
  position: number | null
  offeredSeat: string | null
  offerExpiresAt: string | null
}

async function call<T>(method: string, url: string, body?: unknown): Promise<T | null> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (method !== 'GET') {
    headers['X-XSRF-TOKEN'] = await ensureCsrf()
    headers['Content-Type'] = 'application/json'
  }
  let response: Response
  try {
    response = await fetch(url, {
      method,
      credentials: 'same-origin',
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, 'UNREACHABLE', 'The library system could not be reached.', {})
  }
  const text = await response.text()
  const data = text ? JSON.parse(text) : null
  if (!response.ok) {
    throw new ApiError(response.status, data?.error ?? 'ERROR', data?.message ?? 'Request failed', data?.details ?? {})
  }
  return data as T | null
}

export async function fetchPlace(): Promise<Place> {
  return (await call<Place>('GET', '/api/waitlist/me')) as Place
}

export async function joinWaitlist(floorPref: string | null): Promise<Place> {
  return (await call<Place>('POST', '/api/waitlist', { floorPref })) as Place
}

export async function leaveWaitlist(): Promise<void> {
  await call('DELETE', '/api/waitlist')
}

export async function acceptOffer(): Promise<Place> {
  return (await call<Place>('POST', '/api/waitlist/accept')) as Place
}
