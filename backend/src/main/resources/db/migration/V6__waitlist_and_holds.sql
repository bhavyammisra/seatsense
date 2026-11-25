-- V6: waitlist and seat holds (WORKPLAN B2.1).
-- A student who finds the library full joins the waitlist. When a seat frees,
-- the next person gets it HELD for 15 minutes (a fresh 15 when they accept), so
-- nobody else can take it while they walk over.

CREATE TABLE waitlist_entry (
    id               BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_id       BIGINT      NOT NULL REFERENCES student (id),
    floor_pref       VARCHAR(4)  REFERENCES floor (id),             -- NULL: any floor
    status           VARCHAR(10) NOT NULL
                     CHECK (status IN ('WAITING', 'OFFERED', 'ACCEPTED', 'EXPIRED', 'CANCELLED', 'DONE')),
    joined_at        TIMESTAMPTZ NOT NULL,
    offered_seat_id  BIGINT      REFERENCES seat (id),
    offer_expires_at TIMESTAMPTZ,
    ended_at         TIMESTAMPTZ,
    -- An offer always names a seat and an expiry; waiting entries have neither.
    CHECK ((status IN ('OFFERED', 'ACCEPTED')) = (offered_seat_id IS NOT NULL AND offer_expires_at IS NOT NULL))
);

-- One live place in the queue per student.
CREATE UNIQUE INDEX waitlist_one_live_per_student
    ON waitlist_entry (student_id) WHERE status IN ('WAITING', 'OFFERED', 'ACCEPTED');

-- First come, first served.
CREATE INDEX waitlist_queue ON waitlist_entry (joined_at) WHERE status = 'WAITING';

CREATE TABLE seat_hold (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    seat_id    BIGINT      NOT NULL REFERENCES seat (id),
    student_id BIGINT      NOT NULL REFERENCES student (id),
    type       VARCHAR(16) NOT NULL CHECK (type IN ('WAITLIST_OFFER')),
    status     VARCHAR(10) NOT NULL CHECK (status IN ('ACTIVE', 'USED', 'EXPIRED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CHECK (expires_at > created_at)
);

-- A seat is held for at most one person, and a person holds at most one seat.
CREATE UNIQUE INDEX seat_hold_one_active_per_seat ON seat_hold (seat_id) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX seat_hold_one_active_per_student ON seat_hold (student_id) WHERE status = 'ACTIVE';
CREATE INDEX seat_hold_expiry ON seat_hold (expires_at) WHERE status = 'ACTIVE';
