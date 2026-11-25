package com.seatsense.student;

import static org.assertj.core.api.Assertions.assertThat;

import com.seatsense.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Runs against the sample students seeded by V1 (SAMPLE0001 to SAMPLE0040). */
class StudentServiceTest extends PostgresIntegrationTest {

    @Autowired
    private StudentService service;

    @Test
    void findsASampleStudentByCard() {
        Student student = service.findByCardId("SAMPLE0001").orElseThrow();

        assertThat(student.getId()).isNotNull();
        assertThat(student.getName()).isEqualTo("Aarav S.");
        assertThat(student.getInitials()).isEqualTo("AS");
        assertThat(student.isSample()).isTrue();
    }

    @Test
    void ignoresTheLineBreakAScannerSendsAndSurroundingSpaces() {
        assertThat(service.findByCardId("SAMPLE0002\r\n")).isPresent();
        assertThat(service.findByCardId("  SAMPLE0002 ")).isPresent();
    }

    @Test
    void unknownCardFindsNobody() {
        assertThat(service.findByCardId("SAMPLE9999")).isEmpty();
    }

    @Test
    void blankOrMissingInputFindsNobody() {
        assertThat(service.findByCardId(null)).isEmpty();
        assertThat(service.findByCardId("")).isEmpty();
        assertThat(service.findByCardId("   \n")).isEmpty();
    }

    @Test
    void inputLongerThanAnyCardFindsNobody() {
        assertThat(service.findByCardId("S".repeat(33))).isEmpty();
    }

    @Test
    void cardNumbersAreCaseSensitive() {
        // Card numbers are stored exactly as printed; guessing at case could match the wrong person.
        assertThat(service.findByCardId("sample0001")).isEmpty();
    }
}
