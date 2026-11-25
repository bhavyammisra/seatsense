package com.seatsense.gate;

import com.seatsense.gate.GateViews.ScanResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /api/gate/scan} (GATE role, docs/api.md). */
@RestController
public class GateController {

    public record ScanRequest(@NotBlank @Size(max = 64) String cardId, @NotBlank @Size(max = 20) String gateId) {
    }

    private final GateService gate;

    public GateController(GateService gate) {
        this.gate = gate;
    }

    @PostMapping("/api/gate/scan")
    public ScanResult scan(@Valid @RequestBody ScanRequest body) {
        return gate.scan(body.cardId(), body.gateId());
    }
}
