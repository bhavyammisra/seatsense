import { Client, type StompSubscription } from '@stomp/stompjs'
import { useEffect, useRef } from 'react'

// One shared STOMP connection to the backend's /ws for the whole page (docs/api.md, WebSocket).
// Topics: /topic/seats/{floorId} (anyone), /topic/availability (anyone), /topic/gate/{gateId} (GATE login only).

function csrfToken(): string {
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/)
  return match ? decodeURIComponent(match[1]) : ''
}

const resubscribers = new Set<() => void>()
let client: Client | null = null

function liveClient(): Client {
  if (!client) {
    client = new Client({
      brokerURL: `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`,
      reconnectDelay: 3000,
      // The server checks the CSRF token on CONNECT, like on every POST. Any API response sets the cookie;
      // a public one, so logged-out visitors do not get a 401 in the console.
      beforeConnect: async (c) => {
        if (!csrfToken()) await fetch('/api/public/floors', { credentials: 'same-origin' })
        c.connectHeaders = { 'X-XSRF-TOKEN': csrfToken() }
      },
      onConnect: () => resubscribers.forEach((subscribe) => subscribe()),
    })
    client.activate()
  }
  return client
}

/** Listen to one topic; returns a function that stops listening. Survives reconnects. */
export function subscribeLive<T>(destination: string, onMessage: (body: T) => void): () => void {
  const c = liveClient()
  let subscription: StompSubscription | null = null
  const subscribe = () => {
    subscription = c.subscribe(destination, (message) => onMessage(JSON.parse(message.body) as T))
  }
  resubscribers.add(subscribe)
  if (c.connected) subscribe()
  return () => {
    resubscribers.delete(subscribe)
    if (c.connected) subscription?.unsubscribe()
  }
}

/**
 * React hook: calls onMessage with each message on the topic while the component is mounted.
 * Pass null to listen to nothing (for example before a floor is chosen).
 */
export function useLive<T>(destination: string | null, onMessage: (body: T) => void): void {
  const handler = useRef(onMessage)
  useEffect(() => {
    handler.current = onMessage
  })
  useEffect(() => {
    if (!destination) return
    return subscribeLive<T>(destination, (body) => handler.current(body))
  }, [destination])
}

/** Reconnect with the current login. Call after logging in or out, so gate topics see the new session. */
export async function reconnectLive(): Promise<void> {
  if (!client) return
  await client.deactivate()
  client.activate()
}
