-- V4: login accounts (WORKPLAN B1.5). Sample accounts are created at startup from .env, never seeded here,
-- so no password (not even a hash) is ever committed.
CREATE TABLE app_user (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR(60)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(12)  NOT NULL CHECK (role IN ('STUDENT', 'LIBRARIAN', 'ADMIN', 'GATE')),
    student_id    BIGINT       UNIQUE REFERENCES student (id),
    -- A student account must point at a student; staff and gate accounts must not.
    CHECK ((role = 'STUDENT') = (student_id IS NOT NULL))
);
