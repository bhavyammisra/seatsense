# SeatSense

Live library seat finder for our Enterprising Web Applications (Java) course project. The full idea is in [docs/PROJECT.md](docs/PROJECT.md) and who builds what is in [docs/WORKPLAN.md](docs/WORKPLAN.md).

## Run the backend

You need JDK 21 or newer and a Postgres 16 database. Maven is not needed: the repo has a Maven wrapper.

The steps below use Docker Desktop for Postgres. Without Docker, any local Postgres 16 works (for example `brew install postgresql@16` and `brew services start postgresql@16` on a Mac): create a database and user with the same values as in your `.env`, skip step 2, and carry on.

1. Create your `.env` from the example and set your own password in it:
   ```
   cp .env.example .env
   ```
2. Start Postgres:
   ```
   docker compose up -d
   ```
3. Start the backend (profile `local` is the default):
   ```
   cd backend
   ./mvnw spring-boot:run
   ```
   On Windows PowerShell use `.\mvnw.cmd spring-boot:run`.

   Always start the backend from the `backend/` folder (this applies to `java -jar` too). It reads the database settings from `../.env`, so started from anywhere else it will not find them.
4. Check it is up: http://localhost:8080/actuator/health should show `{"status":"UP"}`.

On a local run the backend creates **sample** login accounts, all with the `SAMPLE_PASSWORD` from your `.env`: `gate` (scanner and kiosk), `librarian` and `admin` (dashboard), and `sample.student1` to `sample.student5` (linked to the sample students). If `SAMPLE_PASSWORD` is empty it skips them and logs a warning.

The frontend talks to the API with a session cookie. Every POST, PUT and DELETE must send the `XSRF-TOKEN` cookie's value in an `X-XSRF-TOKEN` header, or it is refused with 403.

Flyway applies every migration in `backend/src/main/resources/db/migration` on startup.

To use the demo-only features (the simulator and its buttons on the dashboard), run with the `dev` profile instead:
```
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Stop Postgres with `docker compose down`. Your data stays in a Docker volume; `docker compose down -v` deletes it.

## Run the tests

```
cd backend
./mvnw verify
```

Docker must be running: database tests start their own throwaway Postgres with Testcontainers, so they do not touch your local data. Any test that needs the database extends `PostgresIntegrationTest`.

## Profiles

| Profile | Used for | Database |
|---|---|---|
| `local` (default) | Running on your machine | Postgres from `docker-compose.yml`, settings from `.env` |
| `dev` | Demo rehearsal: `local` plus simulator endpoints | Same as `local` |
| `test` | Automated tests | Testcontainers |
| `cloud` | The hosted demo (see "Hosted demo"): sample accounts and simulator on, serves the built frontend too | From the host's `DB_*` settings |

## Run the frontend

You need Node 24 (or 22). In a second terminal:
```
cd frontend
npm ci
npm run dev
```
Open http://localhost:5173. The dev server sends `/api` and `/ws` to the backend on port 8080.

| Page | Who | Log in as |
|---|---|---|
| `/` | Anyone: live free seats and floor maps, "Join the waitlist" when full | nobody |
| `/kiosk` | The screen at the library gate | `gate` |
| `/scanner` | The phone that scans ID cards at the gate | `gate` |
| `/portal` | Students: waitlist place and held-seat offers | `sample.student1` to `sample.student5` |
| `/dashboard` | Library staff: headcount, occupancy, out-of-service seats, demo controls | `librarian` or `admin` |
| `/cards` | Printable **sample** ID cards with barcodes for the demo | nobody |

All students, cards and accounts above are sample data, never real people.

## Demo on phones (local laptop)

The hosted demo needs none of this. Use it only when running everything from one laptop.

The scanner and portal run on phones that open the laptop by its network address (for example `https://192.168.1.5:5173`). Two things are needed:

1. **https**, because phones only allow the camera on a secure address. With [mkcert](https://github.com/FiloSottile/mkcert):
   ```
   mkcert -install
   mkcert 192.168.1.5 localhost
   ```
   Then start the frontend with the two files it printed:
   ```
   SEATSENSE_HTTPS_CERT=./192.168.1.5+1.pem SEATSENSE_HTTPS_KEY=./192.168.1.5+1-key.pem npm run dev
   ```
   Each phone must trust mkcert's root certificate (`mkcert -CAROOT` shows where it is), or accept the warning once.
2. **Allow the phone's origin for live updates.** Start the backend with the laptop's address added:
   ```
   SEATSENSE_WS_ALLOWED_ORIGINS="https://192.168.1.5:5173,http://localhost:[*]" ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```

The laptop and phones must be on the same network (a phone hotspot works).

## Hosted demo

The demo runs online on [Render](https://render.com) as one web service plus a Postgres database, both described in `render.yaml`. The `Dockerfile` builds the React app and puts it inside the Spring Boot jar, so the pages, the API and live updates share one https address. Phones can open the scanner and portal with no certificates and no shared Wi-Fi.

To set it up (once):

1. Sign in to Render with GitHub and give it access to this repository.
2. New, then Blueprint, then pick this repository. Render reads `render.yaml`.
3. When asked for `SAMPLE_PASSWORD`, enter the password the sample accounts (`gate`, `librarian`, `admin`, `sample.student1` to `5`) should use. Keep it out of the repo.
4. Apply. The first build takes a few minutes. The address appears at the top of the `seatsense` service, for example `https://seatsense-xxxx.onrender.com`. Open `/kiosk`, `/scanner`, `/portal`, `/dashboard` or `/cards` on it.

Things to know about the free plan:

- The service sleeps after 15 minutes without visits, and the first visit after that takes about a minute. Open the site a few minutes before presenting.
- The free database expires 30 days after it is created. Create the blueprint close to the presentation date, or move to a paid plan.
- Only sample data goes here. The simulator is on so the demo script works; reset it from the dashboard after rehearsing.

The site has a privacy policy at `/privacy` and terms at `/terms`, linked from the public page. Set a strong `SAMPLE_PASSWORD`: anyone on the internet can reach the login.

The live demo runs at https://seatsense-8gqa.onrender.com (Shefali's Render workspace).

Render only redeploys on its own when its GitHub app can see this repository. This repository belongs to Bhavyam's account, so until he installs Render's GitHub app on it, start each deploy from the service's page in the Render dashboard (Manual Deploy, then Deploy latest commit).

The Docker build downloads Maven dependencies from Google's mirror of Maven Central (`docker/maven-settings.xml`), because Maven Central rate-limits Render's shared build servers.

## Demo script

PROJECT.md Section 16, with the backend on the `dev` profile:

1. Laptop: `/kiosk` (as `gate`) on the projector. Phone: `/scanner` (as `gate`). Second laptop tab: `/dashboard` (as `librarian`). Print `/cards`.
2. Scan a sample card: the kiosk says "Welcome", counts down 10 seconds and gives the best seat, or tap one. The public site and dashboard update at once.
3. Choose 2 seats on the kiosk and let a friend scan: they sit together.
4. Dashboard, **Exam evening rush**: the library fills floor by floor and the top floor shows Full.
5. On a phone, `/portal` as `sample.student1`: join the waitlist.
6. Scan out someone on the right floor with **Leaving**: the portal shows the held seat at once. Accept, scan that student's card: the kiosk offers "Take my held seat".
7. **Clock +30 min** shows breaks and offers running out and passing to the next person.
8. Dashboard: click a free seat to take it out of service. **Reset** clears everything for the next run.

## Concurrency proof

`session/ConcurrencyProofTest` sends 200 threads through the real session service at once: 200 students on one seat (exactly 1 wins), one student on every seat of a floor (exactly 1 seat), and 200 exits of one session (freed once). It runs in CI with every other test.

