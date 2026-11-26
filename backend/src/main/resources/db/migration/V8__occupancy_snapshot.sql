-- V8: occupancy snapshots for predictions and the busiest-hours heatmap
-- (WORKPLAN B4.1 and B4.3, PROJECT.md Section 11.7).
-- Every 5 minutes the app stores, per floor, how many seats are not free.
-- Rows the demo simulator invents are marked is_sample, so screens can say so.

CREATE TABLE occupancy_snapshot (
    id        BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    floor_id  VARCHAR(4)  NOT NULL REFERENCES floor (id),
    taken     INTEGER     NOT NULL CHECK (taken >= 0),
    total     INTEGER     NOT NULL CHECK (total >= taken),
    taken_at  TIMESTAMPTZ NOT NULL,
    is_sample BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE INDEX occupancy_snapshot_floor_time ON occupancy_snapshot (floor_id, taken_at);
