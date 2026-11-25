package com.seatsense.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.seatsense.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AuthIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JdbcTemplate jdbc;

    private void createStudentAccount(String username, String cardId) {
        Long studentId = jdbc.queryForObject("SELECT id FROM student WHERE card_id = ?", Long.class, cardId);
        users.save(new AppUser(username, encoder.encode("pw-" + username), Role.STUDENT, studentId));
    }

    private static String login(String username, String password) {
        return "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password);
    }

    @Test
    void publicAndHealthNeedNoLogin() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/api/public/availability")).andExpect(status().isNotFound()); // passes security, not built yet
    }

    @Test
    void meWithoutLoginIs401WithErrorBody() throws Exception {
        mvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("NOT_LOGGED_IN"));
    }

    @Test
    void studentLogsInSeesThemselvesAndLogsOut() throws Exception {
        createStudentAccount("t.student.a", "SAMPLE0040");
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/api/auth/login").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(login("t.student.a", "pw-t.student.a")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("STUDENT"))
            .andExpect(jsonPath("$.student.sample").value(true))
            .andExpect(jsonPath("$.student.initials").isNotEmpty());

        mvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("t.student.a"));

        mvc.perform(post("/api/auth/logout").session(session).with(csrf()))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        createStudentAccount("t.student.b", "SAMPLE0039");
        mvc.perform(post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(login("t.student.b", "wrong")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("NOT_LOGGED_IN"));
    }

    @Test
    void postWithoutCsrfTokenIsRefused() throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(login("anyone", "anything")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void adminEndpointsAreForLibrariansAndAdmins() throws Exception {
        mvc.perform(get("/api/admin/headcount").with(user("s").roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/headcount").with(user("g").roles("GATE"))).andExpect(status().isForbidden());
        // 404 means security let it through; the endpoint itself comes in B1.6
        mvc.perform(get("/api/admin/headcount").with(user("l").roles("LIBRARIAN"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/headcount").with(user("a").roles("ADMIN"))).andExpect(status().isNotFound());
    }

    @Test
    void gateEndpointsAreForTheGateOnly() throws Exception {
        mvc.perform(post("/api/gate/scan").with(csrf()).with(user("s").roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/gate/scan").with(csrf()).with(user("l").roles("LIBRARIAN"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/gate/scan").with(csrf()).with(user("g").roles("GATE"))).andExpect(status().isNotFound());
    }

    @Test
    void waitlistIsForStudents() throws Exception {
        mvc.perform(get("/api/waitlist/me").with(user("g").roles("GATE"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/waitlist/me").with(user("s").roles("STUDENT"))).andExpect(status().isNotFound());
    }

    @Test
    void initialsComeFromTheFirstTwoWords() {
        org.assertj.core.api.Assertions.assertThat(AuthController.initials("Aarav S.")).isEqualTo("AS");
        org.assertj.core.api.Assertions.assertThat(AuthController.initials(" ananya  kumar  rao ")).isEqualTo("AK");
    }
}
