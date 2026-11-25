package com.seatsense.common;

/** Throw from any controller or service; the API answers 404 with error NOT_FOUND (docs/api.md). */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
