# SeatSense work plan: who does what, phase by phase

This file splits the build in [docs/PROJECT.md](PROJECT.md) between **Shefali** and **Bhavyam**. It replaces the split in PROJECT.md Section 13.

## How to use this file

**Starting a session (either of you):** tell your Claude Code:

> Read CLAUDE.md, docs/PROJECT.md and docs/WORKPLAN.md. I am **[Shefali / Bhavyam]**. Sync with GitHub first, then start on my first unchecked task whose dependencies are merged.

**Rules for Claude Code working from this file:**
1. Sync with GitHub first (see CLAUDE.md, "Before any work").
2. Only pick tasks with your person's prefix: `S` for Shefali, `B` for Bhavyam.
3. Pick the first unchecked task in the current phase whose "Needs" tasks are all checked (merged into `main`). If none is ready, say what you are waiting on and suggest a task from the next phase or the "can start any time" list.
4. One task (or a few tiny related ones) per branch and pull request. Branch name: `feat/<task-id>-<short-name>`, for example `feat/S1.3-seat-state-machine`.
5. In the same pull request, tick the task's box in this file (`- [x]`). That is how the other person sees progress.
6. A task is done only when its "Done when" is true and tests pass in CI.
7. If a task needs a change in the other person's module or a shared file (`docs/api.md`, migrations, `SeatMap`, `pom.xml`, `package.json`), stop and tell the person. Shared-file changes go in their own small PR that the other person approves.

## Decisions taken for this split

| Decision | Choice | Why |
|---|---|---|
| Java version | **Java 21 (LTS)**. Maven compiles with `release 21`. | Long-term support and fully supported by Spring Boot 3.5. Shefali's JDK 25 builds a `release 21` project fine, and any teammate JDK from 21 up works. Nothing we need is Java 25 only. |
| Spring Boot | Latest 3.5.x | Stable, supports Java 21. |
| Maven | Maven wrapper (`./mvnw`) committed to the repo | Nobody needs to install Maven. |
| Database for Bhavyam | PostgreSQL 16 in Docker (`docker-compose.yml`) | Bhavyam has Docker. |
| Database for Shefali | No Docker. Pure Java tasks need no database. To run the full app locally later, install Postgres.app (task S0.6). | Keeps Shefali unblocked without Docker. |
| Database tests | Testcontainers, run locally by Bhavyam and in **GitHub Actions CI for every pull request** | CI runners have Docker, so Shefali's PRs are tested against real Postgres too. |
| Migrations | Shefali: odd versions (V1, V3, ...). Bhavyam: even (V2, V4, ...). Each person's migrations cover only their own modules' tables. | No numbering clashes. |

## Ownership

| | Shefali | Bhavyam |
|---|---|---|
| Theme | Seat engine, gate flow, the kiosk and seat map | Infrastructure, database guarantees, live updates, waitlist, everything outside the gate |
| Backend packages | `student`, `layout`, `session`, `gate`, `allocation`, `found` | `common` (clock, errors, config), `auth`, `live`, `waitlist`, `analytics`, `simulator` |
| Database | Tables for her packages: `student`, `floor`, `zone`, `seat`, `found_item`, `seat_report` | Docker, Flyway setup, Testcontainers, CI, tables for his packages: `seat_session` constraints and concurrency tests, `scan_event`, `waitlist_entry`, `seat_hold`, `occupancy_snapshot`, `app_user` |
| Frontend | `frontend/src/shared/SeatMap`, `kiosk`, `scanner` | `public`, `portal`, `dashboard` |
| Viva topics | PriorityQueue best seat, group seating search, seat state machine, break holds | Database concurrency guarantee and the 200-thread test, WebSockets, waitlist with timed holds, scheduled jobs, predictions |

Note: `seat_session` belongs to Shefali's `session` package, but its **partial unique indexes and the concurrency test** are Bhavyam's (they need Docker to develop and verify). Bhavyam writes that migration; Shefali writes the service code.

---

## Phase 0: Foundations

Goal: a repo where both of us can build, test and run the skeleton, with CI on every PR.

### Bhavyam (needs Docker)
- [x] **B0.1 Branch protection.** GitHub Settings → Branches → protect `main`: require a pull request with 1 approval, require CI to pass (once B0.5 exists). Only the repo owner can do this.
  - Needs: nothing.
- [x] **B0.2 Backend skeleton.** Spring Boot 3.5.x, Java 21, Maven wrapper, group `com.seatsense`. Dependencies: Web, Data JPA, Validation, Security, WebSocket, Flyway, PostgreSQL driver, Actuator, Testcontainers, JUnit 5. Empty packages from PROJECT.md Section 8. `common/TimeProvider` (injectable clock, with a test-controllable implementation). Security temporarily permits all requests.
  - Needs: nothing.
  - Done when: `./mvnw verify` passes and `/actuator/health` responds.
- [x] **B0.3 Local database.** `docker-compose.yml` with Postgres 16, `application.yml` profiles `local`, `test`, `dev`, and `.env.example`. README section "Run the backend".
  - Needs: B0.2.
- [x] **B0.4 Flyway and Testcontainers base.** Flyway enabled, an abstract `PostgresIntegrationTest` base class using Testcontainers that every database test extends.
  - Needs: B0.2.
- [x] **B0.5 CI.** GitHub Actions workflow on every PR and push to `main`: build and test the backend (Testcontainers runs in CI), build and lint the frontend.
  - Needs: B0.2, S0.1.
  - Done when: a PR shows a green check.

### Shefali (no Docker needed)
- [x] **S0.1 Frontend skeleton.** React + Vite + TypeScript in `frontend/`. Folders `src/shared`, `src/kiosk`, `src/scanner`, `src/public`, `src/portal`, `src/dashboard`. Routes `/kiosk`, `/scanner`, `/`, `/portal`, `/dashboard`. Design tokens (colours, type, spacing) following PROJECT.md Section 17, with light and dark mode. Favicon. Dev proxy to the backend on port 8080.
  - Needs: nothing.
- [x] **S0.2 API contract.** Write `docs/api.md` from PROJECT.md Section 10: every endpoint with request and response JSON, error codes, WebSocket topics and payloads, and the list of domain event names (`SeatTaken`, `SeatFreed`, `BreakStarted`, `BreakEnded`, `SessionEnded`, ...). Bhavyam approves.
  - Needs: nothing.
- [x] **S0.3 Decisions log.** Create `docs/decisions.md` with the decisions in this file and PROJECT.md Section 4, one line each with a date.
  - Needs: nothing.
- [ ] **S0.4 Library survey (real world, with Bhavyam if possible).** Count seats on each floor, sketch the layout (rows, tables), mark charging points, windows and quiet or discussion zones. Note opening and closing times and current library rules. Save as `docs/layout/` (photos of sketches plus a `layout.json` with every seat's code, row, column, table, x, y and features).
  - Needs: nothing.
- [x] **S0.5 V1 migration and seed.** `V1__layout_and_students.sql`: tables `student`, `floor`, `zone`, `seat` (with `version` column). Seed floors and seats from `layout.json`, and about 40 **sample students** with `is_sample = true` and initials only, no photos.
  - Done with the provisional layout (360 tour based, 568 seats). Generated by `frontend/scripts/layout-to-sql.mjs`. Real survey counts (S0.4) will come as a new migration, not by editing V1.
  - Needs: B0.4, S0.4.
- [ ] **S0.6 Local Postgres (optional until Phase 1 testing).** Install Postgres.app, create database `seatsense`, run the backend with profile `local`.
  - Needs: B0.3.

### Both
- [x] **X0.1** Ask sir whether a specific stack is required (Spring Boot and React are assumed). Record the answer in `docs/decisions.md`.
- [ ] **X0.2** Get the submission and demo dates; fill them in below.

**Phase 0 dates:** ___ to ___

---

## Phase 1: Core loop

Goal: scan in on a phone, the seat turns taken on the kiosk and public site in under a second; scan out frees it.

### Shefali
- [x] **S1.1 Layout read API.** JPA entities and repositories for floor, zone, seat. `GET /api/public/floors`, `GET /api/public/floors/{id}/seats` (seat state comes from the session module; return FREE until S1.4 lands).
  - Needs: S0.5.
- [x] **S1.2 Student lookup.** `StudentService.findByCardId`, with tests.
  - Needs: S0.5.
- [x] **S1.3 Seat state machine.** Pure Java domain class for FREE, HELD, TAKEN, ON_BREAK, OUT_OF_SERVICE with every legal transition from PROJECT.md Section 11.4. Illegal transitions throw. Unit test every edge.
  - Needs: nothing (can start any time).
- [ ] **S1.4 Sessions.** `seat_session` entity and service: create a session for a chosen seat, end it on exit. Publishes domain events through Spring's `ApplicationEventPublisher`. Handles the unique-constraint violation from B1.1 by returning 409 with an alternative seat.
  - Needs: S1.2, S1.3, B1.1.
- [ ] **S1.5 Gate endpoints.** `POST /api/gate/scan` (decides ENTRY or EXIT from whether an open session exists), `POST /api/sessions`, `POST /api/sessions/{id}/exit` with LEAVE.
  - Needs: S1.4.
- [x] **S1.6 SeatMap component.** Shared SVG map: renders seats from the layout API at their x, y, shows all five states distinguishable by shape or pattern as well as colour, supports click to select, and accepts live updates. Used by kiosk, public site and dashboard.
  - Needs: S0.1 (use mock data until S1.1 lands).
- [ ] **S1.7 Scanner page.** `/scanner` on the phone: camera reads the ID barcode or QR and posts to `/api/gate/scan`. Manual ID entry as a fallback.
  - Needs: S0.1.
- [ ] **S1.8 Kiosk page.** `/kiosk`: waits for a scan, shows "Welcome, [name]", floor tabs with SeatMap, tap to take a seat, exit screen.
  - Needs: S1.5, S1.6.

### Bhavyam
- [ ] **B1.1 V2 migration: sessions and scans.** `seat_session` and `scan_event` tables, including the two partial unique indexes from PROJECT.md Section 9 (one active session per seat, one per student).
  - Needs: S0.5.
- [ ] **B1.2 Concurrency proof.** Testcontainers test: 200 threads take the same seat at once, exactly one ACTIVE session exists afterwards. Same for one student taking two seats. Runs in CI.
  - Needs: B1.1, S1.4.
- [x] **B1.3 Live module.** STOMP WebSocket config. Listen to domain events and broadcast `/topic/seats/{floorId}` and `/topic/availability` as specified in `docs/api.md`. Frontend helper hook `useLive()` in `frontend/src/shared/live` (shared file: small PR, Shefali approves).
  - Needs: B0.2, S0.2.
- [ ] **B1.4 Public availability.** `GET /api/public/availability` (free and total per floor, FULL flag). Public site page `/`: per-floor counts, FULL state, SeatMap per floor, live updates.
  - Needs: B1.3, S1.1, S1.6.
- [x] **B1.5 Auth.** Spring Security roles STUDENT, LIBRARIAN, ADMIN, GATE. `app_user` table (V4) with sample accounts (passwords in `.env`, never committed). Public endpoints stay open; gate endpoints require GATE; admin endpoints require LIBRARIAN.
  - Needs: B0.2.
- [ ] **B1.6 Librarian dashboard v1.** `/dashboard`: login, live occupancy per floor, headcount, SeatMap per floor. `GET /api/admin/headcount`.
  - Needs: B1.3, B1.5, S1.6.

**Phase 1 done when:** phone scan → seat taken on kiosk, public site and dashboard in under a second; scan out frees it; B1.2 passes in CI.

**Phase 1 dates:** ___ to ___

---

## Phase 2: Smart allocation and waitlist

Goal: full library → join waitlist → someone leaves → offer arrives → expires → passes to the next person. Plus best seat, sit together and breaks.

### Shefali
- [ ] **S2.1 Best seat.** PriorityQueue ranking from PROJECT.md Section 11.1. `choice: BEST`. Unit tests with hand-built layouts.
  - Needs: S1.4 (algorithm itself can start any time).
  - Progress: ranking algorithm done (`allocation/BestSeatRanker`, PR for S2.1). Still to do once S1.4 exists: wire `choice: BEST` and the `SEAT_TAKEN` alternative into the session service. Tick this box then.
- [ ] **S2.2 Same as last time** and **10-second auto-assign** on the kiosk.
  - Needs: S2.1, S1.8.
- [ ] **S2.3 Sit together.** Group search from Section 11.2 (same row, same table, BFS fallback). All seats allocated in one transaction, all or nothing. Kiosk flow for 2 to 4 friends scanning together.
  - Needs: S1.4 (algorithm can start any time).
- [ ] **S2.4 Breaks.** Exit choice LEAVE or BREAK, `POST /api/sessions/{id}/return`, break limit per day, scheduled expiry of breaks using the injectable clock, `BreakEnded` event.
  - Needs: S1.5.
- [ ] **S2.5 Closing-time reset.** Scheduled job ends all sessions at the floor's close time.
  - Needs: S1.4.

### Bhavyam
- [ ] **B2.1 V6 migration: waitlist and holds.** `waitlist_entry`, `seat_hold` tables. Held seats must show as HELD in seat state (coordinate with Shefali: the session module reads active holds).
  - Needs: B1.1.
- [ ] **B2.2 Waitlist service.** FIFO join, leave, position. On `SeatFreed`, offer to the head of the queue (respecting floor preference), create a 15-minute hold, notify via `/user/queue/notifications`. Scheduled job expires offers and moves to the next person. Tests with the fake clock.
  - Needs: B2.1, B1.3.
- [ ] **B2.3 Student portal.** `/portal`: login, join or leave waitlist, live position, offer notification with countdown.
  - Needs: B2.2, B1.5.
- [ ] **B2.4 Public site: FULL flow.** When full, show "Join waitlist" (links to portal login) and "Tell me when free" (stretch hook).
  - Needs: B2.3.

**Phase 2 dates:** ___ to ___

---

## Phase 3: Demo readiness

### Bhavyam
- [ ] **B3.1 Simulator.** Dev-profile `POST /api/sim/rush` replaying an exam-evening scenario (sample students fill the library floor by floor), and `POST /api/sim/clock` to fast-forward time.
- [ ] **B3.2 Hosting.** Decide: run locally on one laptop on a hotspot, or deploy. Make sure phones can reach the kiosk and scanner over the demo network (HTTPS is needed for phone camera access if not on localhost).
- [ ] **B3.3 Dashboard polish.** Seat out-of-service toggle, headcount by floor.

### Shefali
- [ ] **S3.1 Concurrency demo flow.** Two phones tapping the same seat: clear "taken by someone else, try 3F-09" message on the losing phone.
- [ ] **S3.2 Kiosk and scanner polish.** Mobile layout, empty, loading and error states, accessibility, copy review against Section 17.
- [ ] **S3.3 README.** How to run everything locally, how to run the demo, sample card barcodes to print.

### Both
- [ ] **X3.1** Print sample ID cards with barcodes (labelled SAMPLE).
- [ ] **X3.2** Rehearse PROJECT.md Section 16 end to end, twice, on the real demo network.

**Phase 3 dates:** ___ to ___

---

## Phase 4: Stretch (only once Phase 3 is solid)

Take them in this order unless we agree otherwise.

| Bhavyam | Shefali |
|---|---|
| [ ] B4.1 Occupancy snapshots + predictions ("usually frees up around") | [ ] S4.1 Seat preferences (power, window, zone) feeding best-seat ranking |
| [ ] B4.2 "Tell me when Floor N has 5+ free" alerts | [ ] S4.2 Found items: notify last occupant |
| [ ] B4.3 Analytics heatmap, "saw FULL" demand count | [ ] S4.3 Seat report button and staff sweep mode |
| [ ] B4.4 Fire-drill headcount view | [ ] S4.4 Accessible reserved seats |
| [ ] B4.5 Entrance display mode | [ ] S4.5 Desk QR confirmation |

---

## Can start any time (no dependencies)

Useful when blocked:
- Shefali: S1.3 state machine, S2.1 and S2.3 algorithms as pure Java with unit tests, S1.6 SeatMap with mock data, S0.4 library survey.
- Bhavyam: B1.5 auth, B1.3 live module against mock events, B3.2 hosting research.
