-- V2: seat sessions and the raw gate scan log (WORKPLAN B1.1).
-- The session service that writes these rows is Shefali's (S1.4); the database guarantees are here.

-- One row per time a student sits at a seat. A row stays after it ends, so "same seat as last time",
-- analytics and the found-items lookup can read history.
CREATE TABLE seat_session (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_id  BIGINT       NOT NULL REFERENCES student (id),
    seat_id     BIGINT       NOT NULL REFERENCES seat (id),
    status      VARCHAR(10)  NOT NULL CHECK (status IN ('ACTIVE', 'ON_BREAK', 'ENDED')),
    started_at  TIMESTAMPTZ  NOT NULL,
    ended_at    TIMESTAMPTZ,
    break_until TIMESTAMPTZ,                              -- set only while ON_BREAK
    breaks_used SMALLINT     NOT NULL DEFAULT 0 CHECK (breaks_used >= 0),
    end_reason  VARCHAR(16)  CHECK (end_reason IN ('LEAVE', 'BREAK_EXPIRED', 'CLOSING', 'ADMIN')),
    group_id    UUID,                                     -- shared by sessions created by one "Sit together"
    CHECK ((status = 'ENDED') = (ended_at IS NOT NULL)),
    CHECK ((status = 'ENDED') = (end_reason IS NOT NULL)),
    CHECK ((status = 'ON_BREAK') = (break_until IS NOT NULL)),
    CHECK (ended_at IS NULL OR ended_at >= started_at)
);

-- The "one seat, one person" guarantee (PROJECT.md Section 11.3). These hold even if the Java code has a bug:
-- a second open session for the same seat, or for the same student, is rejected by Postgres itself.
CREATE UNIQUE INDEX seat_session_one_open_per_seat
    ON seat_session (seat_id) WHERE status IN ('ACTIVE', 'ON_BREAK');
CREATE UNIQUE INDEX seat_session_one_open_per_student
    ON seat_session (student_id) WHERE status IN ('ACTIVE', 'ON_BREAK');

-- "Same seat as last time": a student's sessions, newest first.
CREATE INDEX seat_session_student_recent ON seat_session (student_id, started_at DESC);

-- Every card scanned at a gate, as it happened. Unknown cards are logged too (student_id NULL), so the
-- library can see how often a card was not recognised.
CREATE TABLE scan_event (
    id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    card_id    VARCHAR(32)  NOT NULL,
    gate_id    VARCHAR(20)  NOT NULL,
    student_id BIGINT       REFERENCES student (id),
    type       VARCHAR(12)  NOT NULL CHECK (type IN ('ENTRY', 'EXIT', 'RETURN', 'UNKNOWN_CARD')),
    scanned_at TIMESTAMPTZ  NOT NULL,
    CHECK ((type = 'UNKNOWN_CARD') = (student_id IS NULL))
);

CREATE INDEX scan_event_scanned_at ON scan_event (scanned_at);
