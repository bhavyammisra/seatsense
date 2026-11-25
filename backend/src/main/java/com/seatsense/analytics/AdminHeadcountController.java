package com.seatsense.analytics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/admin/headcount}: LIBRARIAN or ADMIN only (SecurityConfig, /api/admin/**). */
@RestController
public class AdminHeadcountController {

    private final HeadcountService headcount;

    public AdminHeadcountController(HeadcountService headcount) {
        this.headcount = headcount;
    }

    @GetMapping("/api/admin/headcount")
    public Headcount headcount() {
        return headcount.now();
    }
}
