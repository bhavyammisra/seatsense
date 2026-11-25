package com.seatsense.waitlist;

import com.seatsense.auth.AppUser;
import com.seatsense.auth.AppUserRepository;
import com.seatsense.common.ApiError;
import com.seatsense.waitlist.WaitlistService.Place;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The student's waitlist endpoints (docs/api.md, "Student portal"). STUDENT role only. */
@RestController
@RequestMapping("/api/waitlist")
public class WaitlistController {

    public record JoinRequest(String floorPref) {
    }

    private final WaitlistService waitlist;
    private final AppUserRepository users;

    public WaitlistController(WaitlistService waitlist, AppUserRepository users) {
        this.waitlist = waitlist;
        this.users = users;
    }

    private Long studentOf(Authentication auth) {
        return users.findByUsername(auth.getName())
            .map(AppUser::getStudentId)
            .orElseThrow(WaitlistException::notAStudent);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Place join(Authentication auth, @RequestBody(required = false) JoinRequest body) {
        return waitlist.join(studentOf(auth), body == null ? null : body.floorPref());
    }

    @GetMapping("/me")
    public Place me(Authentication auth) {
        return waitlist.placeOf(studentOf(auth));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(Authentication auth) {
        waitlist.leave(studentOf(auth));
    }

    @PostMapping("/accept")
    public Place accept(Authentication auth) {
        return waitlist.accept(studentOf(auth));
    }

    @ExceptionHandler(WaitlistException.class)
    public ResponseEntity<ApiError> refused(WaitlistException e) {
        return ResponseEntity.status(e.status()).body(ApiError.of(e.errorCode(), e.getMessage()));
    }

    /** Two joins at the same moment: the V6 index lets one through. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> doubleJoin(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiError.of("ALREADY_WAITING", "You are already on the waitlist."));
    }
}
