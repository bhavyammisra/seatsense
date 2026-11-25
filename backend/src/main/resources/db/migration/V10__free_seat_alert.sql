-- V10: "Tell me when Floor N has 5+ free seats" (WORKPLAN B4.2).
-- A student asks once; the alert is sent once, the first time the floor reaches that many free seats.

CREATE TABLE free_seat_alert (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_id BIGINT      NOT NULL REFERENCES student (id),
    floor_id   VARCHAR(4)  NOT NULL REFERENCES floor (id),
    min_free   SMALLINT    NOT NULL CHECK (min_free BETWEEN 1 AND 50),
    status     VARCHAR(10) NOT NULL CHECK (status IN ('ACTIVE', 'SENT', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL,
    ended_at   TIMESTAMPTZ,
    CHECK ((status = 'ACTIVE') = (ended_at IS NULL))
);

-- One live alert per student per floor; asking again changes the number.
CREATE UNIQUE INDEX free_seat_alert_one_active ON free_seat_alert (student_id, floor_id) WHERE status = 'ACTIVE';
