package com.seatsense.auth;

import com.seatsense.common.ApiError;
import com.seatsense.student.StudentRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** POST /api/auth/login, GET /api/auth/me (docs/api.md). Logout is handled by SecurityConfig. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record StudentSummary(long id, String name, String initials, boolean sample) {
    }

    public record Me(String username, Role role, StudentSummary student) {
    }

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;
    private final AppUserRepository users;
    private final StudentRepository students;

    public AuthController(AuthenticationManager authenticationManager, SecurityContextRepository contextRepository,
                          AppUserRepository users, StudentRepository students) {
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
        this.users = users;
        this.students = students;
    }

    @PostMapping("/login")
    public Me login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(body.username(), body.password()));
        if (request.getSession(false) != null) {
            request.changeSessionId(); // no session fixation
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        return me(auth);
    }

    @GetMapping("/me")
    public Me me(Authentication auth) {
        AppUser user = users.findByUsername(auth.getName()).orElseThrow();
        return new Me(user.getUsername(), user.getRole(), student(user.getStudentId()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> badLogin() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiError.of("NOT_LOGGED_IN", "Wrong username or password."));
    }

    private StudentSummary student(Long id) {
        if (id == null) {
            return null;
        }
        return students.findById(id)
            .map(s -> new StudentSummary(s.getId(), s.getName(), s.getInitials(), s.isSample()))
            .orElse(null);
    }
}
