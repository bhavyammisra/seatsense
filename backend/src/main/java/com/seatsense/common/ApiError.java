package com.seatsense.common;

import java.util.Map;

/** The body of every error response (docs/api.md, Errors). */
public record ApiError(String error, String message, Map<String, Object> details) {

    public static ApiError of(String error, String message) {
        return new ApiError(error, message, Map.of());
    }
}
