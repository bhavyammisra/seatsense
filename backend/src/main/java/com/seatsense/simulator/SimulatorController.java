package com.seatsense.simulator;

import com.seatsense.simulator.SimulatorService.RushResult;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo-only endpoints (docs/api.md, "Dev only"). They exist only with the
 * {@code dev} Spring profile, and need a LIBRARIAN or ADMIN login.
 */
@RestController
@Profile("dev")
public class SimulatorController {

    public record RushRequest(String scenario) {
    }

    public record ClockRequest(int advanceMinutes) {
    }

    private final SimulatorService simulator;

    public SimulatorController(SimulatorService simulator) {
        this.simulator = simulator;
    }

    /** Lets the dashboard know the demo controls are available. */
    @GetMapping("/api/sim/status")
    public Map<String, Object> status() {
        return Map.of("available", true, "scenarios", new String[] {"EXAM_EVENING"});
    }

    @PostMapping("/api/sim/rush")
    public RushResult rush(@RequestBody(required = false) RushRequest body) {
        return simulator.examEvening(System.nanoTime());
    }

    @PostMapping("/api/sim/clock")
    public Map<String, Object> clock(@RequestBody ClockRequest body) {
        simulator.advance(body.advanceMinutes());
        return Map.of("advancedMinutes", body.advanceMinutes());
    }

    @PostMapping("/api/sim/reset")
    public Map<String, Object> reset() {
        return Map.of("sessionsEnded", simulator.reset());
    }
}
