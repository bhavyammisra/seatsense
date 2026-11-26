-- V12: "how many students saw FULL" (WORKPLAN B4.3): demand the library cannot see today.
-- One row each time the public site shows the library as full. The server only stores a row
-- when the library really is full at that moment. No names, no addresses: just the time.

CREATE TABLE full_view (
    id      BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    seen_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX full_view_seen_at ON full_view (seen_at);
