package com.seatsense.auth;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the SAMPLE login accounts on a local or dev run, so nobody has to insert password hashes by hand.
 * The password comes from SAMPLE_PASSWORD in .env and is never committed. Accounts that already exist are left alone.
 *
 * <ul>
 *   <li>{@code gate}: the scanner phone and the kiosk (role GATE)</li>
 *   <li>{@code librarian}, {@code admin}: the dashboard</li>
 *   <li>{@code sample.student1} to {@code sample.student5}: linked to sample students SAMPLE0001 to SAMPLE0005</li>
 * </ul>
 */
@Component
@Profile("local")
public class SampleAccounts implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleAccounts.class);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;
    private final String password;

    public SampleAccounts(AppUserRepository users, PasswordEncoder encoder, JdbcTemplate jdbc,
                          @Value("${SAMPLE_PASSWORD:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.jdbc = jdbc;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (password.isBlank()) {
            log.warn("SAMPLE_PASSWORD is not set in .env, so no sample login accounts were created");
            return;
        }
        String hash = encoder.encode(password);
        add("gate", hash, Role.GATE, null);
        add("librarian", hash, Role.LIBRARIAN, null);
        add("admin", hash, Role.ADMIN, null);
        for (int n = 1; n <= 5; n++) {
            List<Long> ids = jdbc.queryForList("SELECT id FROM student WHERE card_id = ?", Long.class,
                "SAMPLE%04d".formatted(n));
            if (!ids.isEmpty()) {
                add("sample.student" + n, hash, Role.STUDENT, ids.getFirst());
            }
        }
    }

    private void add(String username, String hash, Role role, Long studentId) {
        if (!users.existsByUsername(username)) {
            users.save(new AppUser(username, hash, role, studentId));
            log.info("Created sample account {} ({})", username, role);
        }
    }
}
