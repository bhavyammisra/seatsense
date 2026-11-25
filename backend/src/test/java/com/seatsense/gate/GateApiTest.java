package com.seatsense.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatsense.PostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The gate API end to end (docs/api.md, "Gate"), as the kiosk and scanner call it.
 * Uses sample students SAMPLE0026 to SAMPLE0030 and removes their sessions afterwards.
 */
@AutoConfigureMockMvc
class GateApiTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @AfterEach
    void removeTestSessions() {
        jdbc.update("""
            delete from seat_session where student_id in (
              select id from student where card_id between 'SAMPLE0026' and 'SAMPLE0030')""");
    }

    private long student(int n) {
        return jdbc.queryForObject("select id from student where card_id = ?", Long.class, "SAMPLE%04d".formatted(n));
    }

    private ResultActions postAsGate(String url, String body) throws Exception {
        return mvc.perform(post(url).with(user("gate").roles("GATE")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions scan(String card) throws Exception {
        return postAsGate("/api/gate/scan", "{\"cardId\":\"%s\",\"gateId\":\"MAIN\"}".formatted(card));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    @Test
    void unknownCardIs404AndLogged() throws Exception {
        Integer before = jdbc.queryForObject("select count(*) from scan_event where type = 'UNKNOWN_CARD'", Integer.class);

        scan("NOT-A-CARD").andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("UNKNOWN_CARD"));

        assertThat(jdbc.queryForObject("select count(*) from scan_event where type = 'UNKNOWN_CARD'", Integer.class))
            .isEqualTo(before + 1);
    }

    @Test
    void aWholeVisitEntrySeatBreakReturnLeaveAndBackAgain() throws Exception {
        long me = student(26);

        scan("SAMPLE0026").andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("SCANNED"))
            .andExpect(jsonPath("$.mode").value("ENTRY"))
            .andExpect(jsonPath("$.student.name").value("Simran K."))
            .andExpect(jsonPath("$.student.initials").value("SK"))
            .andExpect(jsonPath("$.lastSeat").doesNotExist());

        JsonNode taken = body(postAsGate("/api/sessions",
            "{\"studentId\":%d,\"choice\":{\"type\":\"SEAT\",\"seat\":\"GF-C1\"}}".formatted(me))
            .andExpect(status().isCreated()));
        long sessionId = taken.at("/sessions/0/id").asLong();
        assertThat(taken.at("/sessions/0/seat").asText()).isEqualTo("GF-C1");
        assertThat(taken.at("/sessions/0/status").asText()).isEqualTo("ACTIVE");

        scan("SAMPLE0026").andExpect(jsonPath("$.mode").value("EXIT"))
            .andExpect(jsonPath("$.session.id").value(sessionId));

        postAsGate("/api/sessions/" + sessionId + "/exit", "{\"type\":\"BREAK\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ON_BREAK"))
            .andExpect(jsonPath("$.breakUntil").exists());

        scan("SAMPLE0026").andExpect(jsonPath("$.mode").value("RETURN"))
            .andExpect(jsonPath("$.session.status").value("ACTIVE"))
            .andExpect(jsonPath("$.session.seat").value("GF-C1"));

        postAsGate("/api/sessions/" + sessionId + "/exit", "{\"type\":\"LEAVE\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ENDED"));

        scan("SAMPLE0026").andExpect(jsonPath("$.mode").value("ENTRY"))
            .andExpect(jsonPath("$.lastSeat").value("GF-C1"));
    }

    @Test
    void aTakenSeatAnswers409WithANearbySeatToTry() throws Exception {
        postAsGate("/api/sessions", "{\"studentId\":%d,\"choice\":{\"type\":\"SEAT\",\"seat\":\"GF-C2\"}}"
            .formatted(student(26))).andExpect(status().isCreated());

        postAsGate("/api/sessions", "{\"studentId\":%d,\"choice\":{\"type\":\"SEAT\",\"seat\":\"GF-C2\"}}"
            .formatted(student(27)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("SEAT_TAKEN"))
            .andExpect(jsonPath("$.details.alternative.code").exists())
            .andExpect(jsonPath("$.details.alternative.state").value("FREE"));
    }

    @Test
    void someoneAlreadyInsideCannotTakeASecondSeat() throws Exception {
        postAsGate("/api/sessions", "{\"studentId\":%d,\"choice\":{\"type\":\"SEAT\",\"seat\":\"GF-C3\"}}"
            .formatted(student(26))).andExpect(status().isCreated());

        postAsGate("/api/sessions", "{\"studentId\":%d,\"choice\":{\"type\":\"SEAT\",\"seat\":\"GF-C4\"}}"
            .formatted(student(26)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("ALREADY_INSIDE"));
    }

    @Test
    void bestSeatAndSameAsLastBothSeatTheStudent() throws Exception {
        JsonNode best = body(postAsGate("/api/sessions",
            "{\"studentId\":%d,\"choice\":{\"type\":\"BEST\"}}".formatted(student(28)))
            .andExpect(status().isCreated()));
        String seat = best.at("/sessions/0/seat").asText();
        postAsGate("/api/sessions/" + best.at("/sessions/0/id").asLong() + "/exit", "{\"type\":\"LEAVE\"}")
            .andExpect(status().isOk());

        postAsGate("/api/sessions", "{\"studentId\":%d,\"choice\":{\"type\":\"SAME_AS_LAST\"}}".formatted(student(28)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.sessions[0].seat").value(seat));
    }

    @Test
    void aGroupIsSeatedTogetherUnderOneGroupId() throws Exception {
        JsonNode group = body(postAsGate("/api/sessions",
            "{\"studentId\":%d,\"choice\":{\"type\":\"GROUP\",\"studentIds\":[%d,%d,%d]}}"
                .formatted(student(29), student(29), student(30), student(27)))
            .andExpect(status().isCreated()));

        assertThat(group.get("sessions")).hasSize(3);
        String groupId = group.at("/sessions/0/groupId").asText();
        assertThat(groupId).isNotBlank();
        group.get("sessions").forEach(s -> assertThat(s.get("groupId").asText()).isEqualTo(groupId));
    }

    @Test
    void badChoicesAre400() throws Exception {
        postAsGate("/api/sessions", "{\"studentId\":%d,\"choice\":{\"type\":\"SEAT\"}}".formatted(student(26)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
        postAsGate("/api/sessions", "{\"studentId\":%d,\"choice\":{\"type\":\"WINDOW\"}}".formatted(student(26)))
            .andExpect(status().isBadRequest());
        postAsGate("/api/gate/scan", "{\"gateId\":\"MAIN\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void onlyTheGateAccountCanUseTheGate() throws Exception {
        mvc.perform(post("/api/gate/scan").with(user("s").roles("STUDENT")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"cardId\":\"SAMPLE0026\",\"gateId\":\"MAIN\"}"))
            .andExpect(status().isForbidden());
    }
}
