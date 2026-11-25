package com.seatsense.auth;

/** Who someone is to the app. ADMIN can do everything a LIBRARIAN can (see SecurityConfig). */
public enum Role {
    STUDENT,
    LIBRARIAN,
    ADMIN,
    GATE
}
