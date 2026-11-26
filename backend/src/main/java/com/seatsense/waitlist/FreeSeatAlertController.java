package com.seatsense.waitlist;

import com.seatsense.auth.AppUser;
import com.seatsense.auth.AppUserRepository;
import com.seatsense.common.ApiError;
import com.seatsense.waitlist.FreeSeatAlerts.Alert;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** "Tell me when Floor N has 5+ free seats" (docs/api.md, B4.2). STUDENT role only. */
@RestController
@RequestMapping("/api/alerts")
public class FreeSeatAlertController {

    public record AlertRequest(@NotBlank String floorId, @NotNull Integer minFree) {
    }

    private final FreeSeatAlerts alerts;
    private final AppUserRepository users;

    public FreeSeatAlertController(FreeSeatAlerts alerts, AppUserRepository users) {
        this.alerts = alerts;
        this.users = users;
    }

    private long studentOf(Authentication auth) {
        return users.findByUsername(auth.getName())
            .map(AppUser::getStudentId)
            .orElseThrow(WaitlistException::notAStudent);
    }

    @GetMapping
    public List<Alert> mine(Authentication auth) {
        return alerts.activeFor(studentOf(auth));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Alert set(Authentication auth, @Valid @RequestBody AlertRequest body) {
        return alerts.set(studentOf(auth), body.floorId(), body.minFree());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(Authentication auth, @PathVariable long id) {
        alerts.cancel(studentOf(auth), id);
    }

    @ExceptionHandler(WaitlistException.class)
    public ResponseEntity<ApiError> refused(WaitlistException e) {
        return ResponseEntity.status(e.status()).body(ApiError.of(e.errorCode(), e.getMessage()));
    }
}
