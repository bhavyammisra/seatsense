package com.seatsense.gate;

import com.seatsense.allocation.SeatPreferences;
import com.seatsense.gate.GateViews.SessionView;
import com.seatsense.session.SeatSession;
import com.seatsense.session.SessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /api/sessions}, {@code /exit}, {@code /return} (GATE role, docs/api.md). */
@RestController
public class SessionController {

    /** One of: SEAT (with seat), BEST, SAME_AS_LAST, GROUP (with studentIds, optional seats). */
    public record Choice(@NotNull String type, String seat, List<Long> studentIds, List<String> seats) {
    }

    public record TakeRequest(@NotNull Long studentId, @NotNull @Valid Choice choice) {
    }

    public record TakeResponse(List<SessionView> sessions) {
    }

    public record ExitRequest(@NotNull String type) {
    }

    private final SessionService sessions;
    private final GateService gate;

    public SessionController(SessionService sessions, GateService gate) {
        this.sessions = sessions;
        this.gate = gate;
    }

    @PostMapping("/api/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public TakeResponse take(@Valid @RequestBody TakeRequest body) {
        Choice choice = body.choice();
        List<SeatSession> created = switch (choice.type()) {
            case "SEAT" -> {
                if (choice.seat() == null || choice.seat().isBlank()) {
                    throw new IllegalArgumentException("choice.seat is required for SEAT");
                }
                yield List.of(sessions.takeSeat(body.studentId(), choice.seat()));
            }
            case "BEST" -> List.of(sessions.takeBest(body.studentId(), SeatPreferences.NONE));
            case "SAME_AS_LAST" -> List.of(sessions.takeSameAsLast(body.studentId()));
            case "GROUP" -> sessions.takeGroup(choice.studentIds(), choice.seats());
            default -> throw new IllegalArgumentException("Unknown choice type " + choice.type());
        };
        return new TakeResponse(created.stream().map(gate::view).toList());
    }

    @PostMapping("/api/sessions/{id}/exit")
    public SessionView exit(@PathVariable Long id, @Valid @RequestBody ExitRequest body) {
        SeatSession session = switch (body.type()) {
            case "LEAVE" -> sessions.leave(id);
            case "BREAK" -> sessions.startBreak(id);
            default -> throw new IllegalArgumentException("type must be LEAVE or BREAK");
        };
        return gate.view(session);
    }

    @PostMapping("/api/sessions/{id}/return")
    public SessionView back(@PathVariable Long id) {
        return gate.view(sessions.returnFromBreak(id));
    }

}
