package com.seatsense.gate;

import com.seatsense.common.ApiError;
import com.seatsense.layout.LayoutService;
import com.seatsense.session.InvalidSeatTransitionException;
import com.seatsense.session.SessionException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns gate and session errors into the api.md error body. Only for the gate controllers. */
@RestControllerAdvice(assignableTypes = {GateController.class, SessionController.class})
public class GateErrorHandler {

    private final LayoutService layout;

    public GateErrorHandler(LayoutService layout) {
        this.layout = layout;
    }

    @ExceptionHandler(GateService.UnknownCardException.class)
    public ResponseEntity<ApiError> unknownCard(GateService.UnknownCardException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of("UNKNOWN_CARD", e.getMessage()));
    }

    @ExceptionHandler(SessionException.class)
    public ResponseEntity<ApiError> session(SessionException e) {
        HttpStatus status = e instanceof SessionException.NotFound ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT;
        Map<String, Object> details = new HashMap<>();
        if (e instanceof SessionException.SeatTaken taken) {
            details.put("alternative", taken.alternative() == null ? null : layout.seatView(taken.alternative()).orElse(null));
        }
        if (e instanceof SessionException.LibraryFull) {
            details.put("alternative", null);
        }
        return ResponseEntity.status(status).body(new ApiError(e.errorCode(), e.getMessage(), details));
    }

    @ExceptionHandler(InvalidSeatTransitionException.class)
    public ResponseEntity<ApiError> transition(InvalidSeatTransitionException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of("INVALID_TRANSITION", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiError.of("VALIDATION_FAILED", e.getMessage()));
    }
}
