package com.seatsense.analytics;

import com.seatsense.common.ApiError;
import com.seatsense.layout.LayoutService;
import com.seatsense.layout.LayoutViews.SeatView;
import com.seatsense.session.InvalidSeatTransitionException;
import com.seatsense.session.SessionException;
import com.seatsense.session.SessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /api/admin/seats/{code}/out-of-service} (WORKPLAN B3.3). LIBRARIAN or ADMIN. */
@RestController
public class AdminSeatController {

    public record ServiceRequest(@NotNull Boolean outOfService, String reason) {
    }

    private final SessionService sessions;
    private final LayoutService layout;

    public AdminSeatController(SessionService sessions, LayoutService layout) {
        this.sessions = sessions;
        this.layout = layout;
    }

    @PostMapping("/api/admin/seats/{code}/out-of-service")
    public SeatView setOutOfService(@PathVariable String code, @Valid @RequestBody ServiceRequest body) {
        sessions.setOutOfService(code, body.outOfService());
        return layout.seatView(code).orElseThrow();
    }

    @ExceptionHandler(InvalidSeatTransitionException.class)
    public ResponseEntity<ApiError> notFree(InvalidSeatTransitionException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiError.of("INVALID_TRANSITION", "Only a free seat can be taken out of service."));
    }

    @ExceptionHandler(SessionException.NotFound.class)
    public ResponseEntity<ApiError> unknownSeat(SessionException.NotFound e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of("NOT_FOUND", e.getMessage()));
    }
}
