# SeatSense API contract

Single source of truth for the HTTP API, WebSocket messages and domain events. Both the backend and the frontend follow this file.

**Changing it:** open a small PR that changes only this file, tag the other person, and merge it **before** code that depends on the change. Owners are marked on each endpoint (S = Shefali, B = Bhavyam).

Status: draft v0.2 (WORKPLAN S0.2), after review on PR #4. Everything here can change until Phase 1 is merged.

---

## Conventions

- Base path: `/api`. JSON in and out, UTF-8. Field names in camelCase.
- Times: ISO 8601 with offset, for example `2026-11-25T18:40:00+05:30`. The server's clock is the only source of time (`common/TimeProvider`).
- Seat codes: `<floor>-<row><number>`, for example `2F-C7` (see `docs/layout/README.md`).
- Floor ids: `GF`, `1F`, `2F`, `3F` (four floors, as in the library's 360 walkthrough).
- Auth: session cookie after login (Spring Security). Roles: `STUDENT`, `LIBRARIAN`, `ADMIN`, `GATE`. Endpoints under `/api/public` need no login.
- IDs of students are never shown in public responses.

### Errors

Every error response has this shape:

```json
{
  "error": "SEAT_TAKEN",
  "message": "Seat 2F-C7 was taken a moment ago.",
  "details": {}
}
```

| HTTP | `error` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Bad or missing fields. `details` maps field to problem. |
| 401 | `NOT_LOGGED_IN` | Login needed. |
| 403 | `FORBIDDEN` | Wrong role. |
| 404 | `NOT_FOUND` | Unknown seat, floor, card or session. |
| 404 | `UNKNOWN_CARD` | The scanned card is not in the student table. |
| 409 | `SEAT_TAKEN` | Another session holds the seat. `details.alternative` is a suggested seat (Seat object) or null. |
| 409 | `ALREADY_INSIDE` | The student already has an open session. |
| 409 | `GROUP_NOT_AVAILABLE` | No adjacent block of free seats for the group. |
| 409 | `NO_BREAKS_LEFT` | Daily break limit reached. |
| 409 | `LIBRARY_CLOSED` | Outside opening hours. |
| 409 | `INVALID_TRANSITION` | Seat state machine refused the change (for example returning from a break that already ended). |
| 409 | `ALREADY_WAITING` | The student is already on the waitlist. |
| 409 | `NO_OFFER` | Accepting when there is no open offer (none made, already accepted, or expired). |

---

## Shared objects

### Seat

```json
{
  "code": "2F-C7",
  "floor": "2F",
  "section": "hall",
  "zone": "GENERAL",
  "row": "C",
  "number": 7,
  "tableId": null,
  "gx": 7,
  "gy": 3,
  "power": false,
  "window": false,
  "accessible": false,
  "state": "FREE"
}
```

`state` is one of `FREE`, `TAKEN`, `ON_BREAK`, `HELD`, `OUT_OF_SERVICE` (derived, see PROJECT.md Section 9). `zone` is `QUIET`, `GENERAL` or `DISCUSSION`. `gx`, `gy` are grid positions for drawing.

### Floor

```json
{
  "id": "1F",
  "number": 1,
  "name": "First floor",
  "short": "First",
  "landmark": "Main stairs, open atrium and lift",
  "tourScenes": ["L5", "L6", "L7"],
  "gridWidth": 26,
  "gridHeight": 22,
  "sections": [
    { "id": "tables", "name": "Reading tables", "zone": "GENERAL", "note": "Long tables with a centre divider and charging points.", "bookable": true, "fromRow": 0, "toRow": 18 }
  ],
  "fixtures": [
    { "kind": "table", "section": "tables", "gx": 4, "gy": 1, "w": 8, "h": 1, "label": "Table 1" }
  ]
}
```

`fixtures` are furniture and rooms drawn on the map (`table`, `counter`, `room`, or `area` for parts that cannot be booked). `bookable: false` sections (seminar hall, lounge) have fixtures but no seats. `tourScenes` are the scene labels in the library's 360 walkthrough (https://tiet360.in/library/). The full shape is in `docs/layout/provisional-layout.json`.

### Student (as shown at the gate)

```json
{ "id": 17, "name": "Aarav S.", "initials": "AS", "sample": true }
```

### Session

```json
{
  "id": 501,
  "seat": "2F-C7",
  "status": "ACTIVE",
  "startedAt": "2026-11-25T17:02:11+05:30",
  "breakUntil": null,
  "breaksUsedToday": 0,
  "groupId": null
}
```

`status` is `ACTIVE`, `ON_BREAK` or `ENDED`. `groupId` links sessions created together by "Sit together".

---

## Public (no login)

### `GET /api/public/floors` (S, S1.1)
All floors with their sections, without seats.

Response `200`: `Floor[]`

### `GET /api/public/floors/{floorId}/seats` (S, S1.1)
Every seat on the floor with its current state.

Response `200`: `Seat[]`

### `GET /api/public/availability` (B, B1.4)

Response `200`:
```json
{
  "updatedAt": "2026-11-25T18:40:00+05:30",
  "free": 142,
  "total": 800,
  "full": false,
  "floors": [
    { "id": "GF", "name": "Ground floor", "free": 52, "total": 96, "full": false }
  ]
}
```

### `GET /api/public/predictions` (B, stretch B4.1)

Response `200`:
```json
{ "minFree": 5,
  "floors": [ { "floorId": "3F", "freesUpAround": "19:15:00", "weeksOfData": 4, "sample": false } ] }
```
For today's weekday, each 15-minute slot gets the average number of free seats seen in that slot on the same weekday over the last 4 weeks (from `occupancy_snapshot`, stored every 5 minutes). `freesUpAround` is the first slot later today, before the floor closes, where that average reaches `minFree`. With no history `freesUpAround` is `null` and `weeksOfData` is `0`: never invent a value. `sample` is `true` when any of the history is simulator SAMPLE data, and the screen must then say so.

---

## Gate (role `GATE`)

### `POST /api/gate/scan` (S, S1.5)
Called by the scanner page with the card number it read. The server decides the mode from the student's current session:

| Student's session | Mode | What happens |
|---|---|---|
| None open | `ENTRY` | Kiosk shows the welcome screen and seat picker. |
| `ACTIVE` | `EXIT` | Kiosk asks Leaving or Break. |
| `ON_BREAK`, break not over | `RETURN` | The server returns the student to their seat (same as `POST /api/sessions/{id}/return`). Kiosk shows "Welcome back, seat 2F-C7". No seat picking. |
| `ON_BREAK`, break already over | `ENTRY` | The break expired and the seat was freed, so this is a normal entry. |

Request:
```json
{ "cardId": "102301", "gateId": "MAIN" }
```

Response `200` for an entry:
```json
{ "mode": "ENTRY", "student": { "id": 17, "name": "Aarav S.", "initials": "AS", "sample": true }, "lastSeat": "2F-C7", "heldSeat": null }
```
`lastSeat` powers "Same seat as last time". `heldSeat` is set when the student accepted a waitlist offer; the kiosk then confirms that seat directly.

Response `200` for an exit:
```json
{ "mode": "EXIT", "student": { "...": "..." }, "session": { "...Session": "" } }
```

Response `200` for a return from break:
```json
{ "mode": "RETURN", "student": { "...": "..." }, "session": { "...Session, status ACTIVE": "" } }
```

Errors: `UNKNOWN_CARD`, `LIBRARY_CLOSED`.

Every scan also sends a `SCANNED` message to `/topic/gate/{gateId}` (see WebSocket below) so the kiosk screen, a different device from the phone scanner, knows what to show.

### `POST /api/sessions` (S, S1.5 and S2.x)
Take one or more seats.

Request, one of:
```json
{ "studentId": 17, "choice": { "type": "SEAT", "seat": "2F-C7" } }
{ "studentId": 17, "choice": { "type": "BEST" } }
{ "studentId": 17, "choice": { "type": "SAME_AS_LAST" } }
{ "studentId": 17, "choice": { "type": "GROUP", "studentIds": [17, 23], "seats": ["2F-B7", "2F-B8"] } }
{ "studentId": 17, "choice": { "type": "GROUP", "studentIds": [17, 23] } }
```
For `GROUP`, `seats` is optional: with it, the server checks those exact seats; without it, the server searches (PROJECT.md Section 11.2). Group allocation is all or nothing.

Response `201`:
```json
{ "sessions": [ { "...Session": "" } ] }
```

Errors: `SEAT_TAKEN` (with `details.alternative`), `ALREADY_INSIDE`, `GROUP_NOT_AVAILABLE`, `LIBRARY_CLOSED`.

### `POST /api/sessions/{id}/exit` (S, S1.5 and S2.4)

Request:
```json
{ "type": "LEAVE" }
{ "type": "BREAK" }
```

Response `200`: the updated `Session`. Errors: `NO_BREAKS_LEFT`, `INVALID_TRANSITION`.

### `POST /api/sessions/{id}/return` (S, S2.4)
Ends a break early. The gate calls this logic itself on a `RETURN` scan; the endpoint exists for the kiosk and tests. Response `200`: the updated `Session`. Error: `INVALID_TRANSITION` if the break already expired.

---

## Student portal (role `STUDENT`)

### `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me` (B, B1.5)

Login request:
```json
{ "username": "sample.student1", "password": "..." }
```
`GET /api/auth/me` response: `{ "username": "...", "role": "STUDENT", "student": { "...Student": "" } }`

### `POST /api/waitlist` (B, B2.2)

Request: `{ "floorPref": "2F" }` (`floorPref` may be null for any floor)

Response `201`: same body as `GET /api/waitlist/me` (`status: "WAITING"` and `position`). Errors: `ALREADY_INSIDE` if the student is in the library, `ALREADY_WAITING` if they are already on the waitlist.

### `GET /api/waitlist/me` (B, B2.2)

Response `200`:
```json
{ "status": "OFFERED", "position": 1, "offeredSeat": "3F-H2", "offerExpiresAt": "2026-11-25T18:55:00+05:30" }
```
`status` is `NONE` (not on the waitlist), `WAITING`, `OFFERED` or `ACCEPTED`. A place that ended (`EXPIRED`, `CANCELLED`, or `DONE` once the student sits down) reads as `NONE`. `position` is set only while `WAITING`.

### `DELETE /api/waitlist` (B, B2.2)
Leave the waitlist. Response `204`.

### `POST /api/waitlist/accept` (B, B2.2)
Accept the current offer. Accepting **restarts the hold at a fresh 15 minutes** from the moment of acceptance, so someone who accepts at minute 14 still has time to walk over. An offer can be accepted once; the hold is not extended again. The seat stays HELD until the student scans in at the gate (the hold becomes `USED`) or the 15 minutes run out (`EXPIRED`, and the seat goes to the next person).

Response `200`: same body as `GET /api/waitlist/me`, with `status: "ACCEPTED"` and the new `offerExpiresAt`. Error: `NO_OFFER`.

### `POST /api/seats/{code}/report` (S, stretch S4.3)

Request: `{ "reportedState": "TAKEN" }` or `{ "reportedState": "FREE" }`. Response `202`.

---

## Librarian (role `LIBRARIAN`)

### `GET /api/admin/headcount` (B, B1.6)

Response `200`:
```json
{ "inside": 658, "floors": [ { "id": "GF", "inside": 44 } ], "onBreak": 12 }
```

### `POST /api/admin/seats/{code}/out-of-service` (B, B3.3)

Request: `{ "outOfService": true, "reason": "Broken chair" }`. Response `200`: the updated `Seat`.

### `GET /api/admin/analytics/heatmap?from=2026-11-01&to=2026-11-25` (B, stretch B4.3)

Response `200`:
```json
{ "slots": [ { "weekday": "MONDAY", "time": "18:00", "floor": "2F", "averageTaken": 0.92 } ] }
```

### `POST /api/admin/found-items` (S, stretch S4.2)

Request: `{ "seat": "3F-C12", "description": "Black phone charger" }`. Response `201`. Notifies the seat's last occupant.

---

## Dev only (Spring profile `dev`) (B, B3.1)

| Endpoint | Body | Effect |
|---|---|---|
| `POST /api/sim/rush` | `{ "scenario": "EXAM_EVENING" }` | Sample students fill the library floor by floor. |
| `POST /api/sim/clock` | `{ "advanceMinutes": 30 }` | Moves the server clock forward, so breaks and offers expire on stage. |
| `POST /api/sim/reset` | none | Ends every session and clears waitlists. |
| `POST /api/sim/history` | none | Replaces the SAMPLE occupancy history with 4 invented weeks (marked `is_sample`), for predictions and the heatmap. Real snapshots are not touched. |

These endpoints must not exist outside the `dev` profile.

---

## WebSocket (STOMP)

Endpoint: `/ws` (SockJS not used). The frontend connects with `@stomp/stompjs`.

| Destination | Payload | Sent when |
|---|---|---|
| `/topic/seats/{floorId}` | `{ "code": "2F-C7", "state": "TAKEN", "at": "..." }` | Any seat on that floor changes state. |
| `/topic/availability` | Same body as `GET /api/public/availability` | Any seat changes state (throttled to at most 2 per second). |
| `/topic/gate/{gateId}` | `{ "type": "SCANNED", "mode": "ENTRY", "student": {...}, "lastSeat": "...", "heldSeat": null }` (`mode` is `ENTRY`, `EXIT` or `RETURN`) | A card is scanned at that gate. |
| `/user/queue/notifications` | see below | Something happens to this student. |

**Who can subscribe:** `/topic/seats/*` and `/topic/availability` are public (no names, only seat codes and counts). `/topic/gate/*` carries the student's name, so **only the `GATE` role may subscribe**; the server rejects any other subscription (enforced in B1.3 and B1.5). `/user/queue/notifications` reaches only the logged-in student it belongs to.

Notification payloads:
```json
{ "type": "SEAT_OFFERED", "seat": "3F-H2", "expiresAt": "..." }
{ "type": "OFFER_EXPIRED", "seat": "3F-H2" }
{ "type": "BREAK_ENDING", "seat": "2F-C7", "endsAt": "..." }
{ "type": "BREAK_ENDED", "seat": "2F-C7" }
{ "type": "FOUND_ITEM", "seat": "3F-C12", "description": "Black phone charger" }
```

---

## Domain events (inside the backend)

Published with Spring's `ApplicationEventPublisher` by the module that owns the change. Other modules only listen. Each event is a Java `record`.

| Event | Fields | Published by | Main listeners |
|---|---|---|---|
| `CardScanned` | cardId, gateId, studentId, mode (`ENTRY`, `EXIT`, `RETURN`), at | `gate` (S) | `live` |
| `SeatTaken` | seatCode, floorId, studentId, sessionId, at | `session` (S) | `live`, `analytics`, `waitlist` (marks the student's hold on that seat `USED`) |
| `SeatFreed` | seatCode, floorId, reason (`LEAVE`, `BREAK_EXPIRED`, `CLOSING`, `ADMIN`), at | `session` (S) | `live`, `waitlist`, `analytics` |
| `BreakStarted` | seatCode, sessionId, studentId, until | `session` (S) | `live` |
| `BreakEnded` | seatCode, sessionId, studentId, returned (boolean), at | `session` (S) | `live` |
| `SeatHeld` | seatCode, floorId, studentId, until | `waitlist` (B) | `live` |
| `HoldReleased` | seatCode, floorId, reason (`EXPIRED`, `USED`, `CANCELLED`), at | `waitlist` (B) | `live`, `session` |
| `SeatServiceChanged` | seatCode, floorId, outOfService, at | `layout` (S) via admin endpoint (B) | `live` |
