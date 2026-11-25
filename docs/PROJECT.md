# SeatSense: Library Seat Finder (working title)

Full concept and build plan for our Enterprising Web Applications (Java) course project.

This file is the single source of truth for the project. It is written so that a teammate, or a teammate's Claude Code session, can read it once and have the full context: the problem, the idea, every design decision, the architecture, the data model, the API, the work split and how we collaborate on GitHub.

> **For Claude Code:** read this whole file before doing any work. Then check `docs/api.md` (the API contract) and `CLAUDE.md` (repo rules) once they exist. Only work inside the module owned by the person you are helping, unless they explicitly ask otherwise. See [Section 12](#12-collaboration-two-people-two-claude-code-sessions-one-github-repo).

---

## Table of contents

1. [The problem](#1-the-problem)
2. [The idea in one paragraph](#2-the-idea-in-one-paragraph)
3. [How it works for a student](#3-how-it-works-for-a-student)
4. [Design decisions and the gaps they close](#4-design-decisions-and-the-gaps-they-close)
5. [Features (MVP, then stretch)](#5-features-mvp-then-stretch)
6. [Screens](#6-screens)
7. [Tech stack](#7-tech-stack)
8. [Architecture](#8-architecture)
9. [Data model](#9-data-model)
10. [API and real-time contract](#10-api-and-real-time-contract)
11. [Core algorithms (where the Java goes deep)](#11-core-algorithms-where-the-java-goes-deep)
12. [Collaboration: two people, two Claude Code sessions, one GitHub repo](#12-collaboration-two-people-two-claude-code-sessions-one-github-repo)
13. [Work split](#13-work-split)
14. [Build phases](#14-build-phases)
15. [Testing](#15-testing)
16. [Demo script](#16-demo-script)
17. [Design rules for all UI and copy](#17-design-rules-for-all-ui-and-copy)
18. [Open questions to confirm](#18-open-questions-to-confirm)

---

## 1. The problem

Our campus library already has an ID card scanner at the entrance. Every student scans their ID card when entering and again when leaving, and the system looks up their name and photo.

In exam season the library fills up early in the day. A student who arrives in the late afternoon or evening often walks through all 4 floors and still finds no free seat. This has happened to us personally: two of us went floor by floor and left without a seat.

Two things are missing today:

1. **Nobody can see, before walking in, whether the library has space.** Students waste the trip from their hostel or department.
2. **Nobody inside knows where the free seats are.** Students waste time searching every floor, and seats get "saved" with bags for hours.

The library also has no data on how full it gets, when, or how many students were turned away.

## 2. The idea in one paragraph

We plug into the scan the library already does. When a student scans their ID at the entrance, the gate screen shows a live map of every floor and seat. The student taps a free seat (or lets the system pick the best one) and it is assigned to them. When they scan out, the seat frees instantly. Because every entry and exit already passes through the scanner, the system always knows how many seats are free and which ones. A public, view-only website shows live availability so students can check from their rooms before walking over. If the library is full, they see that immediately and can join a waitlist instead of making the trip.

## 3. How it works for a student

**Checking from the hostel (view-only website)**
- Opens the website: "Library: 12 seats free. Floor 2: 6, Floor 4: 0."
- If it is full: sees "FULL", plus "On weekdays, seats usually start freeing up around [time from real data]", and can join the waitlist or ask for an alert.
- Cannot book a seat from the room. This is deliberate (see decision D1).

**Entering**
1. Scans ID card at the gate (in the demo, a phone camera acts as the scanner).
2. Gate screen: "Welcome, [name]" and the floor maps, free seats highlighted.
3. Taps a seat, or taps "Best seat for me", or "Same seat as last time", or "Sit together (2 to 4)" if friends scan together.
4. If the screen is not touched for 10 seconds, the best seat is assigned automatically so the line keeps moving.
5. The seat turns taken on every screen instantly.

**Leaving**
- Scans out. Gate asks: "Leaving" or "Break (30 min)".
- Leaving: seat frees immediately and the next person on the waitlist is offered it.
- Break: seat shows amber "on break" and is held for 30 minutes, then freed automatically if they do not return.

## 4. Design decisions and the gaps they close

| # | Gap | Decision |
|---|---|---|
| D1 | If students can book from their rooms, people will hoard seats and not show up. | The public website is view-only. Seats are only assigned at the gate scan. The only remote action is joining the waitlist, which gives a short, timed hold. |
| D2 | The ID card only stores a number (barcode or RFID), not the name and photo. Those come from the college database. | Our system keeps its own `student` table keyed by card ID. For the demo it is seeded with clearly labelled **sample students** (initials as avatars, no real photos). In a real deployment it would sync from the college database. |
| D3 | How do we integrate with the existing scanner without the vendor's software? | Most USB barcode scanners act as a keyboard: they type the ID number into the focused text box. So the gate screen is just a web page with a focused input. Alternative: read the existing system's entry log with IT's permission. **Demo:** a phone camera scans a printed sample ID barcode. |
| D4 | If every student spends 30 seconds choosing, a queue forms at the gate in exam season. | One-tap options ("Best seat for me", "Same as last time"), 10-second auto-assign, and optional pre-pick on the phone while walking over (nothing is held until the scan, so D1 still holds). |
| D5 | Students leave for lunch and want their seat back; others save seats with bags. | Scan-out asks "Leaving" or "Break (30 min)". Breaks are visible (amber), auto-expire and are limited (max 2 per day, configurable). |
| D6 | A student sits somewhere other than the seat they were assigned, so the map is wrong. | Version 1: a "This seat is actually taken / actually free" report button for students, plus a staff sweep mode. Version 2 (stretch): a QR sticker on each desk to confirm the seat. |
| D7 | A student leaves without scanning out. | All sessions close at library closing time. Stretch: a "Still in the library?" prompt after a few hours for students who are logged in on their phone. |
| D8 | Two students (or two gate screens) choose the same seat at the same instant. | Enforced in the database: only one active session per seat (see Section 11.3). Exactly one wins; the other is offered the nearest similar seat. |

## 5. Features (MVP, then stretch)

### MVP (must work in the demo)
- [ ] Gate scan in and out (phone camera scanner page + gate kiosk page)
- [ ] Live seat map for every floor (SVG), shared by kiosk, website and entrance display
- [ ] Manual seat pick, "Best seat for me", 10-second auto-assign
- [ ] Sit together: find 2 to 4 adjacent free seats
- [ ] Break hold with auto-expiry
- [ ] Public view-only website: free seats per floor, FULL state
- [ ] Waitlist with a 15-minute hold offer when a seat frees
- [ ] Real-time updates on every screen (WebSockets)
- [ ] Librarian dashboard: live occupancy, headcount per floor
- [ ] Concurrency guarantee: one seat, one person
- [ ] Exam-rush simulator (dev-only) to fill the library realistically for the demo

### Stretch (only after MVP is solid)
- [ ] Predictions: "seats usually free up around X" from historical occupancy
- [ ] Alerts: "tell me when Floor 3 has 5+ free seats"
- [ ] Seat preferences: charging point, window, quiet zone, discussion zone, near a book section
- [ ] "You left something at 3F-12": staff log a found item, system notifies the last occupant
- [ ] Emergency headcount view (fire drill): who is inside, per floor
- [ ] Librarian analytics: busiest hours heatmap, most-used seats, how many students saw FULL (demand the library cannot see today)
- [ ] Accessibility: reserved ground-floor seats
- [ ] Seat report button and staff sweep mode (D6)
- [ ] Desk QR confirmation (D6, version 2)
- [ ] Entrance TV display mode
- [ ] "Still here?" prompt (D7)

## 6. Screens

| Screen | Who | Device in demo | Purpose |
|---|---|---|---|
| Scanner page | Gate | Phone | Uses the camera to read the ID barcode and send it to the server |
| Gate kiosk | Student at entrance | Laptop | Welcome, floor maps, seat pick, sit together, exit choice |
| Public availability site | Anyone | Projector / any phone | Live free seats per floor, FULL state, waitlist, predictions. No booking. |
| Student portal | Logged-in student | Phone | Waitlist status, hold offers, notifications, preferences |
| Librarian dashboard | Library staff | Laptop | Live occupancy, headcount, found items, analytics, seat out-of-service |
| Entrance display (stretch) | Everyone | TV / projector | Big read-only availability board |

The seat map is one shared component used by the kiosk, the public site and the dashboard.

## 7. Tech stack

Confirm with sir whether the course requires a specific stack (for example Servlets/JSP). If not, use:

| Layer | Choice | Why |
|---|---|---|
| Language | Java 21 | Course requirement, modern features (records, virtual threads) |
| Backend | Spring Boot 3 (Web, Data JPA, Security, WebSocket, Validation, Scheduling) | Standard enterprise Java |
| Database | PostgreSQL (via Docker) | Partial unique indexes for the seat concurrency guarantee |
| Migrations | Flyway | Both of us get the same schema automatically |
| Real-time | STOMP over WebSocket (Spring) | Push seat changes to every screen |
| Barcode | ZXing (Java) on the server, plus a JS camera reader on the phone page | ZXing is a Java library, good to show |
| Frontend | React + Vite + TypeScript | Interactive SVG seat maps; easy for both of us with Claude Code |
| Tests | JUnit 5, Spring Boot Test, Testcontainers (Postgres) | Real database in concurrency tests |
| Build | Maven | Standard |
| Hosting (for demo) | Any host that runs a Spring Boot jar and Postgres, or run locally | Decide in Phase 4 |

## 8. Architecture

```
 Phone scanner page ─┐
 Gate kiosk ─────────┤        ┌──────────────────────────────┐
 Public site ────────┼─HTTP──▶│  Spring Boot backend         │──▶ PostgreSQL
 Student portal ─────┤◀──WS───│  gate · seat · session ·     │
 Librarian dashboard ┘        │  waitlist · live · analytics │
                              │  · simulator (dev only)      │
                              └──────────────────────────────┘
```

**Backend modules (Java packages under `backend/src/main/java/.../seatsense/`)**

| Package | Responsibility |
|---|---|
| `student` | Student lookup by card ID, sample seed data |
| `layout` | Floors, zones, seats, seat features, adjacency |
| `gate` | Scan endpoint: decides ENTRY vs EXIT for a card |
| `session` | Seat sessions, seat state machine, break holds, closing-time reset |
| `allocation` | Best-seat ranking, group seating search |
| `waitlist` | FIFO waitlist, hold offers, expiry |
| `live` | WebSocket broadcasting of seat and availability changes |
| `analytics` | Occupancy snapshots, predictions, dashboard data, headcount |
| `found` | Found items linked to last occupant (stretch) |
| `simulator` | Dev-only exam-rush replay |
| `auth` | Spring Security: roles STUDENT, LIBRARIAN, ADMIN, GATE |
| `common` | Errors, config, time provider (so tests and the simulator can fast-forward time) |

**Rule:** all time goes through an injectable `Clock`/`TimeProvider`. This lets the demo "fast-forward 2 hours" and makes expiry logic testable.

**Every state change publishes a domain event** (for example `SeatTaken`, `SeatFreed`, `BreakStarted`). The `live` module listens and broadcasts; the `waitlist` module listens to `SeatFreed` to offer the seat. This keeps modules decoupled so we can each build our parts independently.

## 9. Data model

Flyway migrations live in `backend/src/main/resources/db/migration`. Draft:

```
student          (id, card_id UNIQUE, name, programme, year, is_sample BOOLEAN)
floor            (id, number, name, open_time, close_time)
zone             (id, floor_id, name, type: QUIET | DISCUSSION | GENERAL)
seat             (id, code UNIQUE e.g. "2F-14", floor_id, zone_id, table_id,
                  row_idx, col_idx, x, y,                    -- grid + drawing position
                  has_power, near_window, accessible, out_of_service,
                  version)                                   -- optimistic lock
seat_session     (id, student_id, seat_id, status: ACTIVE | ON_BREAK | ENDED,
                  started_at, ended_at, break_until, breaks_used, end_reason)
                  -- PARTIAL UNIQUE INDEX on (seat_id) WHERE status IN ('ACTIVE','ON_BREAK')
                  -- PARTIAL UNIQUE INDEX on (student_id) WHERE status IN ('ACTIVE','ON_BREAK')
scan_event       (id, card_id, gate_id, type: ENTRY | EXIT, scanned_at)   -- raw log
waitlist_entry   (id, student_id, floor_pref NULLABLE, status: WAITING | OFFERED |
                  ACCEPTED | EXPIRED | CANCELLED, joined_at, offered_seat_id, offer_expires_at)
seat_hold        (id, seat_id, student_id, type: WAITLIST_OFFER | PREPICK,
                  expires_at)                                -- held seats show as HELD
occupancy_snapshot (id, floor_id, taken, total, taken_at)   -- every 5 min, for predictions
seat_report      (id, seat_id, reporter_id, reported_state, created_at, resolved)
found_item       (id, seat_id, description, found_at, last_occupant_id, status)
app_user         (id, username, password_hash, role, student_id NULLABLE)
```

**Seat state is derived, not stored as a free-text column:**
- `OUT_OF_SERVICE` if `seat.out_of_service`
- `TAKEN` if an ACTIVE session exists
- `ON_BREAK` if an ON_BREAK session exists
- `HELD` if an unexpired `seat_hold` exists
- `FREE` otherwise

## 10. API and real-time contract

The authoritative version will live in `docs/api.md`. Change it in a PR **before** changing code that depends on it, and tag the other person.

**Gate**
```
POST /api/gate/scan            { cardId, gateId }
  → { mode: "ENTRY" | "EXIT", student: {id, name, initials}, session?: {...} }
POST /api/sessions             { studentId, choice: { type: "SEAT", seatId }
                                                  | { type: "BEST" }
                                                  | { type: "SAME_AS_LAST" }
                                                  | { type: "GROUP", studentIds: [...] } }
  → 201 { sessions: [...] }   | 409 { error: "SEAT_TAKEN", alternative: seat }
POST /api/sessions/{id}/exit   { type: "LEAVE" | "BREAK" }
POST /api/sessions/{id}/return
```

**Public (no login)**
```
GET /api/public/availability            → per-floor { floor, free, total, status }
GET /api/public/floors/{id}/seats       → [{ code, x, y, state, features }]
GET /api/public/prediction              → { usuallyFreesAround, basedOnWeeks }  (stretch)
```

**Student portal (login)**
```
POST   /api/waitlist        { floorPref? }  → { position }
DELETE /api/waitlist
POST   /api/waitlist/accept → confirms the offered hold (still needs a gate scan to sit)
POST   /api/seats/{code}/report { state }
```

**Librarian**
```
GET  /api/admin/headcount
GET  /api/admin/analytics/heatmap?from&to
POST /api/admin/seats/{code}/out-of-service
POST /api/admin/found-items { seatCode, description }
```

**Dev only (profile `dev`)**
```
POST /api/sim/rush  { scenario: "EXAM_EVENING" }
POST /api/sim/clock { advanceMinutes }
```

**WebSocket topics (STOMP)**
```
/topic/seats/{floorId}     → { code, state }            on every seat change
/topic/availability        → per-floor counts            on every change
/user/queue/notifications  → { type: "SEAT_OFFERED" | "BREAK_ENDING" | "FOUND_ITEM", ... }
```

## 11. Core algorithms (where the Java goes deep)

These are what we should explain in the viva.

### 11.1 Best seat for me (PriorityQueue ranking)
For each FREE seat compute a score:
```
score = w1 * preferenceMatch      (power, window, zone type the student prefers)
      + w2 * (1 - localDensity)   (fraction of neighbours taken: spreads people out, quieter)
      - w3 * walkingCost          (distance from entrance / stairs on that floor)
      + w4 * sameAsLastBonus
```
Use a `PriorityQueue<ScoredSeat>` (max-heap) and return the top seat, keeping the next few as alternatives if the first is taken concurrently.

### 11.2 Sit together (group seating search)
Given group size k (2 to 4):
1. Same row: sliding window over `col_idx` per row to find k consecutive FREE seats.
2. Same table: seats sharing `table_id` with k FREE.
3. Fallback: BFS over the seat adjacency graph to find the smallest connected cluster of k FREE seats.
Rank candidate groups with the 11.1 score averaged over members. Allocate all k seats in one transaction (all or nothing).

### 11.3 One seat, one person (concurrency)
Two layers:
- **Database guarantee:** partial unique index on `seat_session(seat_id) WHERE status IN ('ACTIVE','ON_BREAK')`. A second insert for the same seat fails no matter what the Java code does.
- **Application handling:** catch the constraint violation, return 409 with the next-best alternative from 11.1.
- Optimistic locking (`@Version` on `seat`) for admin edits.
- **Proof test:** 200 threads try to take the same seat at once; exactly 1 succeeds (Section 15). We show this live in the demo.

### 11.4 Seat state machine
```
FREE ──scan in──▶ TAKEN ──exit: break──▶ ON_BREAK ──return──▶ TAKEN
  ▲                 │                       │
  │                 └──exit: leave──────────┤ break expired
  │                                         ▼
  └──────────────────────────────────── FREE ◀── closing-time reset
FREE ──waitlist offer──▶ HELD ──accepted + scan in──▶ TAKEN
                          └──offer expired──▶ FREE (offer goes to next in queue)
```
Illegal transitions throw. Unit-test every edge.

### 11.5 Waitlist (FIFO queue with timed holds)
- On `SeatFreed`, if the waitlist is non-empty: create a 15-minute `seat_hold` for the head of the queue (respecting floor preference) and notify them.
- A scheduled job (every 30 s) expires holds and offers the seat to the next person.
- Library-wide fairness: first come, first served. No priority classes in v1.

### 11.6 Break expiry and closing reset
Scheduled jobs: end expired breaks (seat freed, waitlist triggered), and at `close_time` end every open session with `end_reason = CLOSING`.

### 11.7 Predictions (stretch)
Every 5 minutes store an `occupancy_snapshot`. Prediction for a weekday and 15-minute slot = average occupancy for that slot over the last N weeks. "Usually frees up around X" = first upcoming slot where predicted free seats ≥ a threshold. Show "based on N weeks of data"; with no data, show nothing rather than inventing a number.

## 12. Collaboration: two people, two Claude Code sessions, one GitHub repo

We are two people. Both of us use Claude Code. We collaborate through one GitHub repository.

### 12.1 Repo setup (Shefali does this once)
1. Create a private GitHub repo, for example `seatsense`.
2. Add the teammate as a collaborator.
3. Commit this file as `docs/PROJECT.md`.
4. Add a root `CLAUDE.md` with the rules in 12.4 and the line "Read docs/PROJECT.md before any work."
5. Protect `main`: changes only through pull requests, with 1 approval required.
6. Create GitHub Issues from Section 14, labelled by owner and phase.

### 12.2 Repo layout
```
seatsense/
  CLAUDE.md
  README.md                 how to run locally
  docker-compose.yml        postgres
  docs/
    PROJECT.md              this file
    api.md                  API contract (single source of truth)
    decisions.md            short log of decisions we make along the way
  backend/                  Spring Boot (Maven)
  frontend/                 React + Vite
```

### 12.3 Daily workflow
1. `git pull` on `main`, then create a branch: `feat/<area>-<short-name>` (for example `feat/allocation-group-seating`).
2. Tell your Claude Code session which issue and which module you own. Example prompt: "Read docs/PROJECT.md and docs/api.md. I own the allocation module. Implement issue #12 (group seating search) with tests."
3. Keep PRs small (one feature or fix). Open the PR, link the issue, and request the other person's review.
4. The reviewer pulls the branch, runs it and the tests, and approves or comments.
5. Merge with squash. Delete the branch.
6. Any change to `docs/api.md`, the database migrations or shared components goes in its own PR first, and the other person must approve it.

### 12.4 Rules (put these in `CLAUDE.md`)
- Read `docs/PROJECT.md` and `docs/api.md` before starting.
- Work only in modules owned by the person you are helping (Section 13). If a change is needed in the other person's module, stop and say so; do not edit it.
- Never change an existing Flyway migration. Add a new one. Coordinate numbering: Shefali uses odd versions (V1, V3, ...), teammate uses even (V2, V4, ...).
- Every new backend behaviour comes with a test.
- All time goes through the injectable clock.
- Commit messages: short, imperative, written as the person committing. No AI attribution lines, no "Co-Authored-By" lines, no "Generated with" lines, in commits, PRs, issues or the README.
- Never commit secrets (`.env`, passwords, keys). Use `.env.example`.
- Follow the design rules in Section 17 for any UI or copy.
- Sample data must be labelled as sample data. Never invent statistics.

### 12.5 Avoiding merge conflicts
- Each person owns separate packages and separate frontend folders (Section 13).
- Shared files (`api.md`, migrations, `SeatMap` component, `pom.xml`, `package.json`) change only in small dedicated PRs.
- Pull `main` into your branch at least once a day.

## 13. Work split

Split so that **both of us own real Java logic and real UI**, and can each explain our part in the viva.

| | Shefali | Teammate |
|---|---|---|
| Theme | The gate and the seat engine | The live layer and everything outside the gate |
| Backend packages | `student`, `layout`, `gate`, `session`, `allocation` | `waitlist`, `live`, `analytics`, `simulator`, `found` |
| Algorithms to explain | Best-seat PriorityQueue, group seating search, concurrency guarantee, seat state machine | Waitlist with timed holds, scheduled expiry jobs, WebSocket broadcasting, predictions |
| Frontend | `frontend/src/kiosk`, `frontend/src/scanner`, shared `SeatMap` component | `frontend/src/public`, `frontend/src/portal`, `frontend/src/dashboard` |
| Shared, built together in Phase 0 | Data model, `docs/api.md`, domain events list, auth roles, floor layout seed data | same |

`auth` and `common` are set up together in Phase 0, then changed only through dedicated PRs.

## 14. Build phases

Dates are placeholders. Fill them in once we know the submission and demo dates.

**Phase 0: Foundations (together) [dates: ___]**
- Repo, `CLAUDE.md`, branch protection, issues
- Spring Boot skeleton, Docker Postgres, Flyway V1, React skeleton
- Agree `docs/api.md`, domain event names, seat layout of our real library (count seats per floor, draw it)
- Seed: floors, seats, sample students

**Phase 1: Core loop [dates: ___]**
- Shefali: gate scan, sessions, manual seat pick, state machine, concurrency guarantee, kiosk + scanner pages, `SeatMap`
- Teammate: WebSocket broadcasting, public availability site, librarian live occupancy + headcount
- Done when: scan in on phone → seat taken on kiosk and public site in under a second; scan out frees it

**Phase 2: Smart allocation and waitlist [dates: ___]**
- Shefali: best seat, auto-assign after 10 s, same as last time, sit together, break hold
- Teammate: student login + portal, waitlist with hold offers and notifications, expiry jobs, closing reset
- Done when: full library → join waitlist → someone leaves → offer arrives → expires → passes to next

**Phase 3: Demo readiness [dates: ___]**
- Teammate: exam-rush simulator, clock fast-forward
- Shefali: concurrency demo (two phones, same seat), seat out-of-service
- Both: polish UI, mobile layout, empty and error states, README run instructions

**Phase 4: Stretch (only if Phase 3 is solid) [dates: ___]**
- Pick from the stretch list in Section 5, in this order: predictions, preferences, found items, headcount drill view, analytics heatmap, entrance display

## 15. Testing

- **Unit:** state machine transitions, best-seat scoring, group search (rows, tables, BFS fallback), waitlist ordering, break expiry with a fake clock.
- **Concurrency:** 200 threads (or virtual threads) attempt the same seat at once against a real Postgres (Testcontainers). Assert exactly one ACTIVE session. Same test for one student taking two seats.
- **Integration:** full scan-in, scan-out, waitlist offer flow through the HTTP API.
- **Manual demo rehearsal:** run Section 16 end to end at least twice before the presentation, on the same network setup we will use.

## 16. Demo script

Devices: one phone (scanner), one laptop (gate kiosk), projector (public site and dashboard), one or two more phones (student portal, concurrency demo). Printed **sample** ID cards with barcodes.

1. **Scan in.** Phone scans a sample card. Kiosk: "Welcome", floor maps. Tap a seat. On the projected public site, that seat turns taken instantly.
2. **Friends.** Two cards scanned, "Sit together (2)". Two adjacent seats light up.
3. **Exam rush.** Trigger the simulator. The library fills floor by floor. The public site shows FULL.
4. **From the hostel.** A classmate's phone (the "hostel") sees FULL and joins the waitlist: "You're #1".
5. **A seat opens.** Someone scans out choosing "Leaving". The waitlisted phone gets "Seat 3F-08 held for you for 15 minutes".
6. **Break.** Another student scans out on "Break". Seat turns amber. Fast-forward 30 minutes; it frees and goes to the next in the waitlist.
7. **Same seat, same second.** Two phones tap the same seat together. Exactly one gets it; the other is offered the nearest similar seat. Then show the 200-thread test passing.
8. **Librarian view.** Live headcount per floor (and, if built, the busiest-hours heatmap and fire-drill view).

## 17. Design rules for all UI and copy

The project must look deliberately designed by a person, not generated.

**Never use:** purple gradients, pill-shaped buttons, emoji icons, em dashes in UI text, over-the-top scroll animations, cursor animations, AI-generated stock imagery, filler or buzzword copy, fake reviews or testimonials, fake metrics or statistics, made-up headline claims, fake customer logos.

**Do:**
- One clear accent colour, real typography, plain direct copy ("12 seats free", not marketing lines).
- Seat states must be distinguishable without colour alone (shape or pattern as well as colour): FREE, TAKEN, ON BREAK, HELD, OUT OF SERVICE.
- Works on a phone screen with no horizontal scrolling.
- If a real number is not available, show nothing or a clearly marked placeholder. Never invent one.
- Add a favicon.
- All demo data labelled as sample data.

## 18. Open questions to confirm

- [ ] Does the course require a specific stack (Spring Boot vs Servlets/JSP, React vs JSP/Thymeleaf)? Ask sir.
- [ ] Submission and demo dates (fill Section 14).
- [ ] Talk to the librarian: would they want this? What scanner and software does the gate use? Does the ID card use a barcode, QR or RFID? What does the card number look like?
- [ ] Exact seat layout of each floor (count and sketch), including charging points, windows, quiet zones.
- [ ] Library opening and closing times, and current rules on breaks or maximum stay.
- [ ] Where we host it for the demo, or whether we run it locally.
- [ ] Final project name.
