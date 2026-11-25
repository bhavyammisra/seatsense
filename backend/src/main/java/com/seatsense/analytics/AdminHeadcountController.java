package com.seatsense.analytics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/admin/headcount} and {@code /api/admin/fire-drill}: LIBRARIAN or ADMIN only (SecurityConfig, /api/admin/**). */
@RestController
public class AdminHeadcountController {

    private final HeadcountService headcount;
    private final FireDrillService fireDrill;

    public AdminHeadcountController(HeadcountService headcount, FireDrillService fireDrill) {
        this.headcount = headcount;
        this.fireDrill = fireDrill;
    }

    @GetMapping("/api/admin/headcount")
    public Headcount headcount() {
        return headcount.now();
    }

    /** Fire-drill list: every student inside or on a break, by floor, with names (B4.4). Staff only. */
    @GetMapping("/api/admin/fire-drill")
    public FireDrillService.FireDrill fireDrill() {
        return fireDrill.now();
    }
}
