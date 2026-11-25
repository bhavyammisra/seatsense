# Decisions log

One line per decision, newest at the bottom. Add a line whenever we choose between real alternatives. Format: date, decision, why, who.

| Date | Decision | Why | Who |
|---|---|---|---|
| 2026-11-25 | Project: SeatSense, a library seat finder built on the existing ID-card gate scan. | Real problem we have faced in exam season; reuses hardware the library already has. | Both |
| 2026-11-25 | Public website is view-only; seats are assigned only at the gate scan. | Booking from rooms would let people hoard seats and not show up. | Both |
| 2026-11-25 | Only remote action is joining a waitlist, which gives a 15-minute hold when a seat frees. | Fair middle ground between view-only and booking. | Both |
| 2026-11-25 | Scan-out asks Leaving or Break (30 min, limited per day). | Keeps seats for short breaks without allowing bag-saving for hours. | Both |
| 2026-11-25 | One active session per seat and per student enforced by partial unique indexes in Postgres. | Guarantee holds even if application code has a bug; we can prove it with a 200-thread test. | Both |
| 2026-11-25 | Java 21 (LTS), Spring Boot 3.5.x, Maven wrapper. | Long-term support; Shefali's JDK 25 builds a release-21 project; no Java 25-only features needed. | Both |
| 2026-11-25 | Database, Docker, Testcontainers and CI work assigned to Bhavyam. | He has Docker; CI runs database tests for both of us on every PR. | Both |
| 2026-11-25 | Frontend: React + Vite + TypeScript, plain CSS with design tokens, no UI framework. | Full control over the look; avoids a generic template feel. | Shefali |
| 2026-11-25 | Seat picker modelled on cinema ticket apps: floor tabs with free counts, "How many seats?", tap to fill a group together, bottom confirm bar, ticket-style confirmation. | Students already know this pattern from movie booking, so it needs no explanation at the gate. | Shefali |
| 2026-11-25 | Seats drawn as HTML buttons in a CSS grid instead of SVG. | Keyboard focus, screen reader labels and click handling come for free; still crisp at any size. | Shefali |
| 2026-11-25 | Seat states differ by shape and pattern as well as colour (outlined, solid, struck through, dashed, striped, dotted). | Readable for colour-blind students and in bright light at the gate. | Shefali |
| 2026-11-25 | Accent colour: Thapar maroon. Fonts: IBM Plex Sans and IBM Plex Mono (seat codes). | Ties the product to the campus; mono makes seat codes easy to read. | Shefali |
| 2026-11-25 | Provisional layout follows the official 360 walkthrough (tiet360.in/library): 4 floors, atrium with long tables, atrium rail and window counters, glass discussion rooms, seminar hall not bookable. 568 bookable seats. | The tour shows what is on each floor; seat counts are estimates until the on-site survey (S0.4). Replaces the earlier 5-floor, 800-seat guess. | Shefali |
| 2026-11-25 | Seat codes `<floor>-<row><number>`, row letters skip I and O. | Short, readable aloud, no confusion with 1 and 0. | Shefali |
| 2026-11-25 | The phone scanner and the kiosk are separate devices linked by `/topic/gate/{gateId}`. | Matches the real gate (fixed scanner, separate screen) and the demo (phone plus laptop). | Shefali |
