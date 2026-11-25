package com.seatsense;

import static org.assertj.core.api.Assertions.assertThat;

import com.seatsense.common.TimeProvider;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

class SeatsenseApplicationTests extends PostgresIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void startsAndReportsHealthy() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Autowired
    private TimeProvider time;

    @Test
    void clockUsesLibraryTimeZone() {
        assertThat(time.clock().getZone()).isEqualTo(ZoneId.of("Asia/Kolkata"));
    }

    @Test
    void flywayRanAgainstPostgres() {
        Integer historyTables = jdbc.queryForObject(
            "select count(*) from information_schema.tables where table_name = 'flyway_schema_history'",
            Integer.class);

        assertThat(historyTables).isEqualTo(1);
    }
}
