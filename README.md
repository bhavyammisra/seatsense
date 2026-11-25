# SeatSense

Live library seat finder for our Enterprising Web Applications (Java) course project. The full idea is in [docs/PROJECT.md](docs/PROJECT.md) and who builds what is in [docs/WORKPLAN.md](docs/WORKPLAN.md).

## Run the backend

You need JDK 21 or newer and a Postgres 16 database. Maven is not needed: the repo has a Maven wrapper.

The steps below use Docker Desktop for Postgres. Without Docker, any local Postgres 16 works (for example Postgres.app on a Mac): create a database and user with the same values as in your `.env`, skip step 2, and carry on.

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

Flyway applies every migration in `backend/src/main/resources/db/migration` on startup.

To use the demo-only features (simulator, once B3.1 exists), run with the `dev` profile instead:
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
