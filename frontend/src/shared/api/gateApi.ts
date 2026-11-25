import { ensureCsrf } from './authApi'

// Client for the gate API (docs/api.md, "Gate"). GATE login only.

export type ScanMode = 'ENTRY' | 'EXIT' | 'RETURN'

export type StudentView = { id: number; name: string; initials: string; sample: boolean }

export type SessionView = {
  id: number
  seat: string
  status: 'ACTIVE' | 'ON_BREAK' | 'ENDED'
  startedAt: string
  breakUntil: string | null
  breaksUsedToday: number
  groupId: string | null
}

export type ScanResult = {
  type: 'SCANNED'
  mode: ScanMode
  student: StudentView
  lastSeat: string | null
  heldSeat: string | null
  session: SessionView | null
}

export type Choice =
  | { type: 'SEAT'; seat: string }
  | { type: 'BEST' }
  | { type: 'SAME_AS_LAST' }
  | { type: 'GROUP'; studentIds: number[]; seats?: string[] }

/** An error answer from the API, in the api.md shape. */
export class ApiError extends Error {
  readonly status: number
  readonly error: string
  readonly details: Record<string, unknown>
  constructor(status: number, error: string, message: string, details: Record<string, unknown>) {
    super(message)
    this.status = status
    this.error = error
    this.details = details
  }
}

async function post<T>(url: string, body?: unknown): Promise<T> {
  const token = await ensureCsrf()
  let response: Response
  try {
    response = await fetch(url, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, 'UNREACHABLE', 'The library system could not be reached.', {})
  }
  const text = await response.text()
  const data = text ? JSON.parse(text) : null
  if (!response.ok) {
    throw new ApiError(
      response.status,
      data?.error ?? 'ERROR',
      data?.message ?? `Request failed (${response.status})`,
      data?.details ?? {},
    )
  }
  return data as T
}

export function scanCard(cardId: string, gateId: string): Promise<ScanResult> {
  return post<ScanResult>('/api/gate/scan', { cardId, gateId })
}

export async function takeSeats(studentId: number, choice: Choice): Promise<SessionView[]> {
  const result = await post<{ sessions: SessionView[] }>('/api/sessions', { studentId, choice })
  return result.sessions
}

export function exitSession(sessionId: number, type: 'LEAVE' | 'BREAK'): Promise<SessionView> {
  return post<SessionView>(`/api/sessions/${sessionId}/exit`, { type })
}
